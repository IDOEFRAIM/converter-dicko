package com.converter.order.service;

import com.converter.audit.domain.AuditAction;
import com.converter.audit.service.AuditService;
import com.converter.common.api.PageResponse;
import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.order.domain.Beneficiary;
import com.converter.order.domain.Order;
import com.converter.order.domain.OrderStatus;
import com.converter.order.domain.OrderStatusHistory;
import com.converter.order.dto.BeneficiaryRequest;
import com.converter.order.dto.BeneficiaryResponse;
import com.converter.order.dto.CreateOrderRequest;
import com.converter.order.dto.OrderDetailResponse;
import com.converter.order.dto.OrderFeasibilityResponse;
import com.converter.order.dto.OrderStatusHistoryResponse;
import com.converter.order.dto.OrderSummaryResponse;
import com.converter.order.repository.BeneficiaryRepository;
import com.converter.order.repository.OrderRepository;
import com.converter.order.repository.OrderStatusHistoryRepository;
import com.converter.quote.domain.Quote;
import com.converter.quote.domain.QuoteStatus;
import com.converter.quote.repository.QuoteRepository;
import com.converter.security.OwnershipService;
import com.converter.settings.domain.SettingKey;
import com.converter.settings.service.SettingsService;
import com.converter.supplier.domain.Supplier;
import com.converter.supplier.domain.SupplierStatus;
import com.converter.supplier.repository.SupplierRepository;
import com.converter.treasury.domain.Currency;
import com.converter.treasury.service.TreasuryService;
import com.converter.user.domain.User;
import com.converter.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Orchestration du cycle de vie d'un ordre.
 *
 * <p>Toute transition de statut passe par cette classe : elle seule
 * combine {@link OrderStateMachine} (legalite), le verrou pessimiste
 * (concurrence), l'ecriture de {@link OrderStatusHistory} (tracabilite)
 * et l'audit. Les modules {@code payment} et {@code settlement}
 * declenchent une transition en appelant les methodes
 * {@code transitionToXxx} ci-dessous — jamais en modifiant {@link Order}
 * directement.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final QuoteRepository quoteRepository;
    private final SupplierRepository supplierRepository;
    private final UserRepository userRepository;
    private final OrderStateMachine stateMachine;
    private final TreasuryService treasuryService;
    private final SettingsService settingsService;
    private final OwnershipService ownershipService;
    private final AuditService auditService;
    private final Clock clock;

    public OrderService(OrderRepository orderRepository,
                        BeneficiaryRepository beneficiaryRepository,
                        OrderStatusHistoryRepository historyRepository,
                        QuoteRepository quoteRepository,
                        SupplierRepository supplierRepository,
                        UserRepository userRepository,
                        OrderStateMachine stateMachine,
                        TreasuryService treasuryService,
                        SettingsService settingsService,
                        OwnershipService ownershipService,
                        AuditService auditService,
                        Clock clock) {
        this.orderRepository = orderRepository;
        this.beneficiaryRepository = beneficiaryRepository;
        this.historyRepository = historyRepository;
        this.quoteRepository = quoteRepository;
        this.supplierRepository = supplierRepository;
        this.userRepository = userRepository;
        this.stateMachine = stateMachine;
        this.treasuryService = treasuryService;
        this.settingsService = settingsService;
        this.ownershipService = ownershipService;
        this.auditService = auditService;
        this.clock = clock;
    }

    // -----------------------------------------------------------------
    // Creation
    // -----------------------------------------------------------------

    /**
     * Indique, <b>avant</b> la saisie du beneficiaire, si un ordre pourra etre cree a partir de
     * ce devis : le devis appartient bien a l'utilisateur, il est {@code ACCEPTED}, et la
     * liquidite CNY disponible couvre actuellement son montant (si la reservation est active).
     * Lecture seule, aucun solde de tresorerie renvoye — voir {@link OrderFeasibilityResponse}.
     */
    @Transactional(readOnly = true)
    public OrderFeasibilityResponse checkFeasibility(UUID quoteId, UUID userId) {
        Quote quote = quoteRepository.findById(quoteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUOTE_NOT_FOUND,
                        "Devis introuvable : " + quoteId));
        ownershipService.assertOwnedBy(quote.getUserId(), userId, ErrorCode.QUOTE_NOT_FOUND,
                "Devis introuvable : " + quoteId);

        boolean reservationEnabled = settingsService.getBoolean(SettingKey.TREASURY_RESERVE_ON_ORDER);
        boolean sufficient = !reservationEnabled
                || treasuryService.snapshot(Currency.CNY).available().compareTo(quote.getAmountCny()) >= 0;
        return new OrderFeasibilityResponse(quote.getId(), quote.getAmountCny(), reservationEnabled, sufficient);
    }

    @Transactional
    public OrderDetailResponse create(CreateOrderRequest request, UUID userId) {
        Quote quote = quoteRepository.findById(request.quoteId())
                .orElseThrow(() -> new BusinessException(ErrorCode.QUOTE_NOT_FOUND,
                        "Devis introuvable : " + request.quoteId()));
        ownershipService.assertOwnedBy(quote.getUserId(), userId, ErrorCode.QUOTE_NOT_FOUND,
                "Devis introuvable : " + request.quoteId());

        if (quote.getStatus() != QuoteStatus.ACCEPTED) {
            throw new BusinessException(ErrorCode.QUOTE_NOT_ACCEPTED,
                    "Le devis doit etre accepte avant de creer un ordre (statut actuel : "
                            + quote.getStatus() + ").");
        }
        // Defense en profondeur : verifie explicitement avant d'inserer,
        // meme si la contrainte SQL uq_orders_quote (V9) est le filet de
        // securite ultime contre une double creation concurrente.
        if (orderRepository.existsByQuoteId(quote.getId())) {
            throw new BusinessException(ErrorCode.QUOTE_ALREADY_USED,
                    "Ce devis a deja ete utilise pour creer un ordre.");
        }

        assertWithinAmountBounds(quote.getAmountXof());
        assertKycVerifiedIfRequired(userId, quote.getAmountXof());
        assertOpenOrderLimitNotReached(userId);

        // Resolu et valide AVANT toute ecriture : un fournisseur invalide/inactif/d'un autre
        // utilisateur ne doit jamais laisser un Order orphelin sans Beneficiary derriere lui.
        BeneficiaryRequest beneficiaryRequest = resolveBeneficiaryRequest(request, userId);

        Instant now = clock.instant();
        Duration paymentWindow = Duration.ofMinutes(settingsService.getInt(SettingKey.ORDER_PAYMENT_WINDOW_MINUTES));
        String reference = orderRepository.nextReference();
        Order order = new Order(reference, userId, quote.getId(), quote.getAmountXof(), quote.getAmountCny(),
                quote.getCustomerRate(), quote.getFeeXof(), quote.getNetAmountXof(), request.note(),
                now, now.plus(paymentWindow), request.supplierId(), request.purpose(), request.purposeDetails());
        Order saved;
        try {
            saved = orderRepository.saveAndFlush(order);
        } catch (DataIntegrityViolationException ex) {
            // uq_orders_quote : deux creations concurrentes ont franchi le existsByQuoteId
            // ci-dessus ; l'une gagne, l'autre atterrit ici. Le code metier precis, jamais
            // le DUPLICATE_RESOURCE generique du GlobalExceptionHandler (mission passe 2, P2-6).
            throw new BusinessException(ErrorCode.QUOTE_ALREADY_USED,
                    "Ce devis a deja ete utilise pour creer un ordre.");
        }

        Beneficiary beneficiary = new Beneficiary(saved.getId(), beneficiaryRequest.type(),
                beneficiaryRequest.fullName(), beneficiaryRequest.identifier(),
                beneficiaryRequest.bankName(), beneficiaryRequest.bankBranch(), now);
        beneficiaryRepository.save(beneficiary);

        recordHistory(saved.getId(), null, OrderStatus.AWAITING_PAYMENT, userId, null, now);

        if (settingsService.getBoolean(SettingKey.TREASURY_RESERVE_ON_ORDER)) {
            treasuryService.reserve(Currency.CNY, quote.getAmountCny(), saved.getId(), userId);
            saved.markTreasuryReserved();
            orderRepository.save(saved);
        }

        String supplierMetadata = request.supplierId() == null ? "" : ",\"supplierId\":\"" + request.supplierId() + "\"";
        auditService.record(userId, null, AuditAction.ORDER_CREATED, "Order", saved.getId().toString(),
                "{\"quoteId\":\"" + quote.getId() + "\",\"reference\":\"" + reference + "\"" + supplierMetadata + "}");
        log.info("Ordre {} cree pour {} a partir du devis {}", reference, userId, quote.getId());

        return toDetail(saved, beneficiary, List.of(
                new OrderStatusHistoryResponse(null, OrderStatus.AWAITING_PAYMENT, userId, null, now)));
    }

    /**
     * Exactement l'un de {@code beneficiary} ou {@code supplierId} doit etre fourni. Lorsqu'un
     * fournisseur est utilise, ses coordonnees sont copiees dans un {@link BeneficiaryRequest}
     * ordinaire : le reste du flux (creation du snapshot {@link Beneficiary}) reste strictement
     * identique, qu'il vienne d'une saisie directe ou d'un fournisseur enregistre.
     */
    private BeneficiaryRequest resolveBeneficiaryRequest(CreateOrderRequest request, UUID userId) {
        if (request.supplierId() != null) {
            if (request.beneficiary() != null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Fournir soit un beneficiaire, soit un fournisseur enregistre (supplierId), jamais les deux.");
            }
            Supplier supplier = supplierRepository.findById(request.supplierId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.SUPPLIER_NOT_FOUND,
                            "Fournisseur introuvable : " + request.supplierId()));
            ownershipService.assertOwnedBy(supplier.getOwnerUserId(), userId, ErrorCode.SUPPLIER_NOT_FOUND,
                    "Fournisseur introuvable : " + request.supplierId());
            if (supplier.getStatus() != SupplierStatus.ACTIVE) {
                throw new BusinessException(ErrorCode.SUPPLIER_INACTIVE,
                        "Ce fournisseur est desactive et ne peut plus etre utilise pour un nouvel ordre.");
            }
            // Nom legal si disponible (traçabilite financiere), sinon le nom d'affichage du carnet.
            String fullName = supplier.getLegalName() != null ? supplier.getLegalName() : supplier.getDisplayName();
            return new BeneficiaryRequest(supplier.getType(), fullName, supplier.getAccountNumber(),
                    supplier.getBankName(), supplier.getBankBranch());
        }
        if (request.beneficiary() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Un beneficiaire ou un fournisseur enregistre (supplierId) est obligatoire.");
        }
        return request.beneficiary();
    }

    // -----------------------------------------------------------------
    // Consultation (cote client)
    // -----------------------------------------------------------------

    @Transactional(readOnly = true)
    public OrderDetailResponse get(UUID orderId, UUID userId) {
        Order order = loadOwned(orderId, userId);
        return toDetailWithHistory(order);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> listMine(UUID userId, Pageable pageable) {
        Page<Order> page = orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return PageResponse.from(page, OrderService::toSummary);
    }

    // -----------------------------------------------------------------
    // Annulation (client)
    // -----------------------------------------------------------------

    @Transactional
    public OrderDetailResponse cancel(UUID orderId, UUID userId, String reason) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> notFound(orderId));
        ownershipService.assertOwnedBy(order.getUserId(), userId, ErrorCode.ORDER_NOT_FOUND,
                "Ordre introuvable : " + orderId);

        transition(order, OrderStatus.CANCELLED, userId, reason);
        order.setCancellationReason(reason);
        releaseReservationIfNeeded(order, userId, "Annulation : " + reason);
        orderRepository.save(order);

        auditService.record(userId, null, AuditAction.ORDER_CANCELLED, "Order", orderId.toString(), null);
        return toDetailWithHistory(order);
    }

    // -----------------------------------------------------------------
    // Consultation admin
    // -----------------------------------------------------------------

    @Transactional(readOnly = true)
    public OrderDetailResponse adminGet(UUID orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> notFound(orderId));
        return toDetailWithHistory(order);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> adminList(OrderStatus status, Pageable pageable) {
        Page<Order> page = status == null
                ? orderRepository.findAll(pageable)
                : orderRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        return PageResponse.from(page, OrderService::toSummary);
    }

    // -----------------------------------------------------------------
    // Transitions declenchees par d'autres modules (payment, settlement)
    // -----------------------------------------------------------------

    @Transactional
    public void transitionToPaymentSubmitted(UUID orderId, UUID actorId) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> notFound(orderId));
        transition(order, OrderStatus.PAYMENT_SUBMITTED, actorId, null);
        orderRepository.save(order);
        auditService.record(actorId, null, AuditAction.ORDER_PAYMENT_SUBMITTED, "Order", orderId.toString(), null);
    }

    @Transactional
    public void transitionToPaymentVerified(UUID orderId, UUID actorId) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> notFound(orderId));
        transition(order, OrderStatus.PAYMENT_VERIFIED, actorId, null);
        orderRepository.save(order);
        auditService.record(actorId, null, AuditAction.ORDER_PAYMENT_VERIFIED, "Order", orderId.toString(), null);
    }

    @Transactional
    public void transitionToRejected(UUID orderId, UUID actorId, String reason) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> notFound(orderId));
        transition(order, OrderStatus.REJECTED, actorId, reason);
        order.setRejectionReason(reason);
        releaseReservationIfNeeded(order, actorId, "Paiement rejete : " + reason);
        orderRepository.save(order);
        auditService.record(actorId, null, AuditAction.ORDER_REJECTED, "Order", orderId.toString(), null);
    }

    @Transactional
    public void transitionToProcessing(UUID orderId, UUID actorId) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> notFound(orderId));
        transition(order, OrderStatus.PROCESSING, actorId, null);
        orderRepository.save(order);
        auditService.record(actorId, null, AuditAction.ORDER_PROCESSING_STARTED, "Order", orderId.toString(), null);
    }

    @Transactional
    public void transitionToCompleted(UUID orderId, UUID actorId) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> notFound(orderId));
        transition(order, OrderStatus.COMPLETED, actorId, null);
        orderRepository.save(order);
        auditService.record(actorId, null, AuditAction.ORDER_COMPLETED, "Order", orderId.toString(), null);
    }

    /**
     * Expire l'ordre s'il est encore {@code AWAITING_PAYMENT} et que son echeance de paiement est
     * atteinte a {@code asOf} — et libere alors sa reservation CNY. Idempotent : toute autre
     * situation (deja paye, deja annule, deja expire, echeance non atteinte) est un no-op
     * silencieux. Appele par {@link OrderExpirationService} (piloté par un scheduler), une
     * transaction par ordre, verrou pessimiste pris ici.
     *
     * @return {@code true} si CET appel a effectivement expire l'ordre
     */
    @Transactional
    public boolean expireIfOverdue(UUID orderId, Instant asOf) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null
                || order.getStatus() != OrderStatus.AWAITING_PAYMENT
                || !order.isPaymentOverdue(asOf)) {
            return false;
        }
        transition(order, OrderStatus.EXPIRED, null, "Echeance de paiement depassee");
        releaseReservationIfNeeded(order, null, "Ordre expire (paiement non recu dans le delai imparti)");
        orderRepository.save(order);
        auditService.recordSystem(AuditAction.ORDER_EXPIRED, "Order", orderId.toString(),
                "{\"deadlineAt\":\"" + order.getPaymentDeadlineAt() + "\"}");
        log.info("Ordre {} expire (echeance {} depassee), reservation liberee", order.getReference(),
                order.getPaymentDeadlineAt());
        return true;
    }

    /** Identifiants des ordres candidats a l'expiration a {@code asOf} : {@code AWAITING_PAYMENT} et echus. */
    @Transactional(readOnly = true)
    public List<UUID> findOverdueOrderIds(Instant asOf) {
        return orderRepository.findOverdueAwaitingPaymentIds(asOf);
    }

    @Transactional(readOnly = true)
    public UUID ownerOf(UUID orderId) {
        return orderRepository.findById(orderId).map(Order::getUserId).orElse(null);
    }

    /** Lecture directe pour les modules {@code payment}/{@code settlement} (verification de precondition). */
    @Transactional(readOnly = true)
    public Order getEntityOrThrow(UUID orderId) {
        return orderRepository.findById(orderId).orElseThrow(() -> notFound(orderId));
    }

    @Transactional(readOnly = true)
    public Beneficiary getBeneficiaryOrThrow(UUID orderId) {
        return beneficiaryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Beneficiaire introuvable pour l'ordre " + orderId));
    }

    // -----------------------------------------------------------------

    private void assertWithinAmountBounds(BigDecimal amountXof) {
        BigDecimal min = settingsService.getDecimal(SettingKey.MIN_ORDER_AMOUNT_CFA);
        BigDecimal max = settingsService.getDecimal(SettingKey.MAX_ORDER_AMOUNT_CFA);
        if (amountXof.compareTo(min) < 0 || amountXof.compareTo(max) > 0) {
            throw new BusinessException(ErrorCode.ORDER_AMOUNT_OUT_OF_RANGE,
                    "Le montant doit etre compris entre " + min + " et " + max + " XOF.");
        }
    }

    /**
     * KYC obligatoire (inclus) a partir de {@code SettingKey.KYC_REQUIRED_THRESHOLD_XOF} —
     * verification minimale (drapeau administrateur, voir {@code User#verifyKyc}), jamais un
     * moteur de conformite complet. Seuil configurable, jamais code en dur (meme convention que
     * {@link #assertWithinAmountBounds}).
     */
    private void assertKycVerifiedIfRequired(UUID userId, BigDecimal amountXof) {
        BigDecimal threshold = settingsService.getDecimal(SettingKey.KYC_REQUIRED_THRESHOLD_XOF);
        if (amountXof.compareTo(threshold) < 0) {
            return;
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Utilisateur introuvable : " + userId));
        if (!user.isKycVerified()) {
            throw new BusinessException(ErrorCode.KYC_VERIFICATION_REQUIRED,
                    "La verification d'identite (KYC) est obligatoire pour un ordre de " + amountXof
                            + " XOF ou plus (seuil actuel : " + threshold + " XOF).");
        }
    }

    private void assertOpenOrderLimitNotReached(UUID userId) {
        int maxOpen = settingsService.getInt(SettingKey.MAX_OPEN_ORDERS_PER_USER);
        long openOrders = orderRepository.countOpenOrdersByUser(userId);
        if (openOrders >= maxOpen) {
            throw new BusinessException(ErrorCode.TOO_MANY_OPEN_ORDERS,
                    "Nombre maximal d'ordres ouverts atteint (" + maxOpen + ").");
        }
    }

    private void transition(Order order, OrderStatus target, UUID actorId, String reason) {
        stateMachine.assertTransition(order.getStatus(), target);
        Instant now = clock.instant();
        OrderStatus previous = order.getStatus();
        order.applyStatus(target, now);
        recordHistory(order.getId(), previous, target, actorId, reason, now);
    }

    private void releaseReservationIfNeeded(Order order, UUID actorId, String reason) {
        if (order.isTreasuryReserved()) {
            treasuryService.release(Currency.CNY, order.getAmountCny(), order.getId(), actorId, reason);
            order.markTreasuryReleased();
        }
    }

    private void recordHistory(UUID orderId, OrderStatus from, OrderStatus to, UUID changedBy,
                               String reason, Instant now) {
        historyRepository.save(new OrderStatusHistory(orderId, from, to, changedBy, reason, now));
    }

    private Order loadOwned(UUID orderId, UUID userId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> notFound(orderId));
        ownershipService.assertOwnedBy(order.getUserId(), userId, ErrorCode.ORDER_NOT_FOUND,
                "Ordre introuvable : " + orderId);
        return order;
    }

    private BusinessException notFound(UUID orderId) {
        return new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Ordre introuvable : " + orderId);
    }

    private OrderDetailResponse toDetailWithHistory(Order order) {
        Beneficiary beneficiary = getBeneficiaryOrThrow(order.getId());
        List<OrderStatusHistoryResponse> history = historyRepository
                .findByOrderIdOrderByCreatedAtAsc(order.getId()).stream()
                .map(h -> new OrderStatusHistoryResponse(h.getFromStatus(), h.getToStatus(), h.getChangedBy(),
                        h.getReason(), h.getCreatedAt()))
                .toList();
        return toDetail(order, beneficiary, history);
    }

    private static OrderDetailResponse toDetail(Order order, Beneficiary beneficiary,
                                                List<OrderStatusHistoryResponse> history) {
        return new OrderDetailResponse(
                order.getId(), order.getReference(), order.getQuoteId(), order.getStatus(),
                order.getAmountXof(), order.getAmountCny(), order.getCustomerRate(), order.getFeeXof(),
                order.getNetAmountXof(), order.getNote(), order.getCancellationReason(),
                order.getRejectionReason(),
                new BeneficiaryResponse(beneficiary.getType(), beneficiary.getFullName(),
                        beneficiary.getIdentifier(), beneficiary.getBankName(), beneficiary.getBankBranch()),
                history, order.getCreatedAt(), order.getUpdatedAt(), order.getPaymentDeadlineAt(),
                order.getCompletedAt(), order.getCancelledAt(),
                order.getSupplierId(), order.getPurpose(), order.getPurposeDetails());
    }

    private static OrderSummaryResponse toSummary(Order order) {
        return new OrderSummaryResponse(order.getId(), order.getReference(), order.getStatus(),
                order.getAmountXof(), order.getAmountCny(), order.getCreatedAt());
    }
}
