package com.converter.transfi.service;

import com.converter.audit.domain.AuditAction;
import com.converter.audit.service.AuditService;
import com.converter.common.api.PageResponse;
import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.common.util.JsonUtil;
import com.converter.config.props.TransFiProperties;
import com.converter.ledger.domain.LedgerEntryType;
import com.converter.ledger.service.LedgerService;
import com.converter.order.domain.Beneficiary;
import com.converter.order.domain.Order;
import com.converter.order.domain.OrderStatus;
import com.converter.order.service.OrderService;
import com.converter.transfi.client.TransFiApiException;
import com.converter.transfi.client.TransFiClient;
import com.converter.transfi.client.TransFiCreateOrderRequest;
import com.converter.transfi.client.TransFiOrderResult;
import com.converter.transfi.client.TransFiOrderType;
import com.converter.transfi.domain.TransfiOrder;
import com.converter.transfi.domain.TransfiOrderDirection;
import com.converter.transfi.domain.TransfiOrderStatus;
import com.converter.transfi.domain.TransfiWebhookEvent;
import com.converter.transfi.dto.TransfiOrderResponse;
import com.converter.transfi.repository.TransfiOrderRepository;
import com.converter.transfi.repository.TransfiWebhookEventRepository;
import com.converter.treasury.domain.Currency;
import com.converter.treasury.service.TreasuryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Orchestre le payin/payout automatises via TransFi (voir {@code docs/TRANSFI_INTEGRATION.md}).
 *
 * <p><b>Phase 1 (cette classe, aujourd'hui)</b> : jamais declenchee automatiquement a la creation
 * d'un ordre — {@link #routePayin} est un geste EXPLICITE d'un administrateur (voir
 * {@code AdminTransfiController}), le flux manuel (declaration + verification) reste le chemin
 * par defaut pour tous les clients. Reconnecter cette classe a la creation d'ordre (payin
 * automatique pour le client) est une Phase 2, a faire seulement apres validation du contrat reel
 * de l'API en sandbox (voir {@code TransFiHttpClient}).
 *
 * <p>Les transitions d'ordre ({@code PAYMENT_VERIFIED -> PROCESSING -> COMPLETED}) et la
 * consommation de la reservation de tresorerie CNY sont EXACTEMENT celles du flux manuel
 * ({@code SettlementService}) : point d'entree commun ({@code OrderService}/{@code TreasuryService}),
 * jamais une seconde implementation qui pourrait diverger.
 */
@Service
public class TransfiOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(TransfiOrchestrationService.class);

    private final TransFiProperties properties;
    private final TransFiClient client;
    private final TransfiOrderRepository repository;
    private final OrderService orderService;
    private final TreasuryService treasuryService;
    private final LedgerService ledgerService;
    private final AuditService auditService;
    private final TransfiWebhookEventRepository webhookEventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public TransfiOrchestrationService(TransFiProperties properties, TransFiClient client,
                                       TransfiOrderRepository repository, OrderService orderService,
                                       TreasuryService treasuryService, LedgerService ledgerService,
                                       AuditService auditService, TransfiWebhookEventRepository webhookEventRepository,
                                       ObjectMapper objectMapper, Clock clock) {
        this.properties = properties;
        this.client = client;
        this.repository = repository;
        this.orderService = orderService;
        this.treasuryService = treasuryService;
        this.ledgerService = ledgerService;
        this.auditService = auditService;
        this.webhookEventRepository = webhookEventRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Point d'entree unique du webhook TransFi (appele par {@code TransfiWebhookController} apres
     * verification de signature). Gere l'idempotence : {@code providerEventId} est insere AVANT
     * tout traitement metier, dans la MEME transaction -- un doublon (contrainte unique violee) est
     * detecte immediatement et ignore sans aucun effet de bord ; si le traitement metier echoue
     * ensuite, toute la transaction (y compris l'insertion de l'evenement) est annulee, ce qui
     * permet a TransFi de renvoyer legitimement le meme webhook plus tard.
     */
    @Transactional
    public void handleWebhook(String eventId, String direction, String providerOrderId, String rawStatus,
                               String rawPayload) {
        if (eventId != null && !eventId.isBlank()) {
            try {
                webhookEventRepository.saveAndFlush(new TransfiWebhookEvent(eventId, direction, clock.instant()));
            } catch (DataIntegrityViolationException e) {
                log.info("Webhook TransFi deja traite (eventId={}) -- ignore.", eventId);
                return;
            }
        } else {
            log.warn("Webhook TransFi recu sans identifiant d'evenement -- idempotence non garantie pour cet appel.");
        }

        if ("payout".equalsIgnoreCase(direction)) {
            handlePayoutResult(providerOrderId, rawStatus, rawPayload);
        } else {
            handlePayinResult(providerOrderId, rawStatus, rawPayload);
        }
    }

    /** Liste des ordres TransFi, du plus recent au plus ancien -- vue de rapprochement (admin). */
    @Transactional(readOnly = true)
    public PageResponse<TransfiOrderResponse> list(Pageable pageable) {
        return PageResponse.from(repository.findAllByOrderByCreatedAtDesc(pageable), TransfiOrderResponse::from);
    }

    /**
     * Route explicitement un ordre {@code AWAITING_PAYMENT} vers un payin TransFi — geste
     * administrateur (Phase 1, voir Javadoc de classe). Ne modifie jamais le statut de l'ordre
     * lui-meme : c'est le webhook de confirmation ({@link #handlePayinResult}) qui le fera, une
     * fois l'encaissement reellement confirme par TransFi, jamais a l'optimiste ici.
     */
    @Transactional
    public TransfiOrder routePayin(UUID orderId, UUID actorId) {
        if (!properties.active()) {
            throw new BusinessException(ErrorCode.TRANSFI_UNAVAILABLE,
                    "Integration TransFi non active (app.transfi.enabled=false ou identifiants absents).");
        }
        Order order = orderService.getEntityOrThrow(orderId);
        if (order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATE,
                    "Un payin TransFi ne peut etre cree que depuis AWAITING_PAYMENT (statut actuel : "
                            + order.getStatus() + ").");
        }
        if (repository.findByOrderIdAndDirection(orderId, TransfiOrderDirection.PAYIN).isPresent()) {
            throw new BusinessException(ErrorCode.TRANSFI_ORDER_ALREADY_ROUTED,
                    "Cet ordre a deja un payin TransFi en cours.");
        }

        Instant now = clock.instant();
        TransfiOrder transfiOrder = new TransfiOrder(orderId, TransfiOrderDirection.PAYIN, now);
        TransFiOrderResult result;
        try {
            result = client.createOrder(new TransFiCreateOrderRequest(TransFiOrderType.PAYIN, order.getAmountXof(),
                    Currency.XOF.name(), order.getReference(), null, null));
        } catch (TransFiApiException e) {
            log.error("Echec de creation du payin TransFi pour l'ordre {}", orderId, e);
            throw new BusinessException(ErrorCode.TRANSFI_UNAVAILABLE,
                    "TransFi n'a pas pu creer le payin. Reessayez, ou utilisez le flux manuel.");
        }
        transfiOrder.applyProviderResult(result.providerOrderId(), result.payUrl(), result.rawJson(), now);
        TransfiOrder saved = repository.saveAndFlush(transfiOrder);

        auditService.record(actorId, null, AuditAction.TRANSFI_PAYIN_CREATED, "Order", orderId.toString(),
                "{\"transfiOrderId\":" + JsonUtil.jsonString(saved.getId().toString()) + "}");
        return saved;
    }

    /**
     * Traite le resultat d'un payin (webhook). Sur succes : {@code PAYMENT_VERIFIED ->
     * PROCESSING} (identique au flux manuel), puis creation immediate du payout correspondant.
     * Sur echec : rejette l'ordre, exactement comme un rejet manuel de preuve de paiement.
     */
    @Transactional
    public void handlePayinResult(String providerOrderId, String rawStatus, String rawPayload) {
        TransfiOrder transfiOrder = repository.findByProviderOrderIdForUpdate(providerOrderId).orElse(null);
        if (transfiOrder == null || transfiOrder.getDirection() != TransfiOrderDirection.PAYIN) {
            log.warn("Webhook TransFi payin recu pour un providerOrderId inconnu : {}", providerOrderId);
            return;
        }
        TransfiOrderStatus status = toStatus(rawStatus);
        Instant now = clock.instant();
        transfiOrder.applyStatus(status, rawPayload, now);

        UUID orderId = transfiOrder.getOrderId();
        if (status == TransfiOrderStatus.SUCCESS) {
            orderService.transitionToPaymentVerified(orderId, null);
            orderService.transitionToProcessing(orderId, null);
            createPayout(orderId, now);
        } else if (status == TransfiOrderStatus.FAILED) {
            orderService.transitionToRejected(orderId, null, "Paiement TransFi refuse ou echoue.");
        }
        // PENDING/CREATED : aucune action, on attend la prochaine notification.
    }

    /** Traite le resultat d'un payout (webhook). Sur succes : consomme la reservation CNY puis complete l'ordre. */
    @Transactional
    public void handlePayoutResult(String providerOrderId, String rawStatus, String rawPayload) {
        TransfiOrder transfiOrder = repository.findByProviderOrderIdForUpdate(providerOrderId).orElse(null);
        if (transfiOrder == null || transfiOrder.getDirection() != TransfiOrderDirection.PAYOUT) {
            log.warn("Webhook TransFi payout recu pour un providerOrderId inconnu : {}", providerOrderId);
            return;
        }
        TransfiOrderStatus status = toStatus(rawStatus);
        Instant now = clock.instant();
        transfiOrder.applyStatus(status, rawPayload, now);

        UUID orderId = transfiOrder.getOrderId();
        if (status == TransfiOrderStatus.SUCCESS) {
            Order order = orderService.getEntityOrThrow(orderId);
            treasuryService.consume(Currency.CNY, order.getAmountCny(), orderId, null);
            orderService.transitionToCompleted(orderId, null);
            recordProviderFeeIfPresent(orderId, rawPayload);
        } else if (status == TransfiOrderStatus.FAILED) {
            // L'argent du client est deja encaisse (payin reussi) : ne JAMAIS annuler l'ordre ici
            // automatiquement, ce serait un double decaissement potentiel ou une perte de tracabilite.
            // Reste PROCESSING -- visible dans la liste admin, intervention manuelle requise (voir
            // docs/TRANSFI_INTEGRATION.md, section "Echec de payout").
            log.error("Payout TransFi echoue pour l'ordre {} (transfiOrder={}) -- intervention manuelle requise.",
                    orderId, transfiOrder.getId());
            auditService.recordSystem(AuditAction.TRANSFI_WEBHOOK_PROCESSED, "Order", orderId.toString(),
                    "{\"payoutFailed\":true}");
        }
    }

    private void createPayout(UUID orderId, Instant now) {
        Order order = orderService.getEntityOrThrow(orderId);
        Beneficiary beneficiary = orderService.getBeneficiaryOrThrow(orderId);

        TransfiOrder transfiOrder = new TransfiOrder(orderId, TransfiOrderDirection.PAYOUT, now);
        TransFiOrderResult result;
        try {
            // CNY uniquement pour l'instant -- un payout USDT exigerait d'etendre Currency
            // (aujourd'hui XOF/CNY seulement), non fait dans cette phase (voir docs/TRANSFI_INTEGRATION.md).
            result = client.createOrder(new TransFiCreateOrderRequest(TransFiOrderType.PAYOUT, order.getAmountCny(),
                    Currency.CNY.name(), order.getReference(), beneficiary.getFullName(), beneficiary.getIdentifier()));
        } catch (TransFiApiException e) {
            // Le payin est deja encaisse : un echec de CREATION du payout (pas d'execution) doit
            // rester visible et actionnable, jamais silencieux -- l'ordre reste PROCESSING.
            log.error("Echec de creation du payout TransFi pour l'ordre {} -- intervention manuelle requise.",
                    orderId, e);
            auditService.recordSystem(AuditAction.TRANSFI_WEBHOOK_PROCESSED, "Order", orderId.toString(),
                    "{\"payoutCreationFailed\":true}");
            return;
        }
        transfiOrder.applyProviderResult(result.providerOrderId(), result.payUrl(), result.rawJson(), now);
        repository.saveAndFlush(transfiOrder);
        auditService.recordSystem(AuditAction.TRANSFI_PAYOUT_CREATED, "Order", orderId.toString(),
                "{\"transfiOrderId\":" + JsonUtil.jsonString(transfiOrder.getId().toString()) + "}");
    }

    /**
     * Best-effort : enregistre le frais prestataire si le webhook en porte un (nom de champ
     * PROVISOIRE, {@code "providerFee"} ou {@code "fee"} — a confirmer contre la documentation
     * officielle). Ne fait jamais echouer le traitement du webhook si le champ est absent ou
     * illisible : un cout non trace vaut mieux qu'un ordre bloque en PROCESSING par une erreur de
     * parsing sur une donnee accessoire.
     */
    private void recordProviderFeeIfPresent(UUID orderId, String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            JsonNode feeNode = root.has("providerFee") ? root.get("providerFee") : root.get("fee");
            if (feeNode == null || feeNode.isNull() || !feeNode.isNumber()) {
                return;
            }
            BigDecimal fee = feeNode.decimalValue();
            if (fee.signum() > 0) {
                ledgerService.record(orderId, LedgerEntryType.PROVIDER_FEE, Currency.CNY, fee,
                        "Frais TransFi (payout), extrait du webhook -- champ provisoire, voir docs/TRANSFI_INTEGRATION.md");
            }
        } catch (Exception e) {
            log.warn("Payload TransFi illisible pour l'extraction du frais prestataire (ordre {}) -- ignore.",
                    orderId, e);
        }
    }

    /**
     * Traduit le statut brut TransFi vers notre vocabulaire stable — PROVISOIRE (les valeurs
     * exactes renvoyees par TransFi ne sont pas confirmees, voir {@code TransFiHttpClient}) :
     * toute valeur non reconnue retombe sur {@code PENDING} (jamais {@code SUCCESS} par defaut,
     * jamais d'exception qui ferait echouer le traitement du webhook).
     */
    private TransfiOrderStatus toStatus(String rawStatus) {
        if (rawStatus == null) {
            return TransfiOrderStatus.PENDING;
        }
        String normalized = rawStatus.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "success", "completed", "paid", "settled" -> TransfiOrderStatus.SUCCESS;
            case "failed", "rejected", "cancelled", "canceled" -> TransfiOrderStatus.FAILED;
            case "created" -> TransfiOrderStatus.CREATED;
            default -> {
                log.warn("Statut TransFi non reconnu : '{}' -- traite comme PENDING.", rawStatus);
                yield TransfiOrderStatus.PENDING;
            }
        };
    }
}
