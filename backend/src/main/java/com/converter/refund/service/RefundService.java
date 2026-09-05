package com.converter.refund.service;

import com.converter.audit.domain.AuditAction;
import com.converter.audit.service.AuditService;
import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.payment.domain.Payment;
import com.converter.payment.domain.PaymentStatus;
import com.converter.payment.repository.PaymentRepository;
import com.converter.refund.domain.Refund;
import com.converter.refund.domain.RefundStatus;
import com.converter.refund.dto.RefundResponse;
import com.converter.refund.repository.RefundRepository;
import com.converter.treasury.domain.Currency;
import com.converter.treasury.service.TreasuryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Remboursement XOF au client — operation admin, independante du {@code Settlement}.
 *
 * <p><b>Ce que "rembourser le client" signifie ici</b> : rendre les XOF que le client a
 * effectivement verses (donc uniquement possible depuis un {@link Payment} {@code CONFIRMED} —
 * avant confirmation, aucun XOF n'a jamais rejoint la tresorerie, il n'y a rien a rembourser).
 * Cela ne pretend jamais annuler un {@code Settlement} deja execute (le decaissement CNY en
 * Chine, une fois fait, reste fait — voir {@link #create}) ; ce n'est pas non plus lie a la
 * reservation CNY de l'{@code Order} (jamais touchee ici, voir {@code TreasuryService#refund}).
 *
 * <p><b>Order.status n'est jamais modifie par ce service.</b> Un remboursement est une verite
 * portee entierement par {@code Refund.status} — introduire un statut {@code REFUNDED} sur
 * {@code Order} aurait fallu re-ouvrir la state machine (dont chaque etat terminal est deja
 * couvert par {@code COMPLETED}/{@code REJECTED}/{@code CANCELLED}/{@code EXPIRED}) sans
 * necessite reelle : "cet ordre a ete rembourse" se repond entierement par
 * {@code GET /admin/payments/{id}/refund}.
 */
@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    /**
     * Statuts "actifs" : au plus un a la fois par paiement (voir {@code uq_refunds_payment_active},
     * migration V20). {@code REJECTED} n'est volontairement pas actif — voir {@link #create} pour
     * le raisonnement metier (mission "Refund retry policy").
     */
    private static final List<RefundStatus> ACTIVE_STATUSES = List.of(RefundStatus.PENDING, RefundStatus.PROCESSED);

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final TreasuryService treasuryService;
    private final AuditService auditService;
    private final Clock clock;

    public RefundService(RefundRepository refundRepository,
                         PaymentRepository paymentRepository,
                         TreasuryService treasuryService,
                         AuditService auditService,
                         Clock clock) {
        this.refundRepository = refundRepository;
        this.paymentRepository = paymentRepository;
        this.treasuryService = treasuryService;
        this.auditService = auditService;
        this.clock = clock;
    }

    /**
     * Cree la decision de remboursement (statut {@code PENDING}), pour le montant exact recu
     * par le client — jamais un montant saisi librement (voir {@code CreateRefundRequest}).
     *
     * <p>Aucun effet tresorerie ici : comme {@code SettlementService#create}, la decision est
     * separee de son execution reelle (voir {@link #process}).
     *
     * <p><b>Une nouvelle tentative est autorisee apres un {@code REJECTED}</b> (mission "Refund
     * retry policy") : rejeter un remboursement est une decision administrative, pas un fait
     * objectif comme un montant de paiement errone — une erreur d'appreciation ("mauvais
     * paiement selectionne", "besoin de plus d'information") doit pouvoir etre corrigee sans
     * intervention manuelle en base. Seul {@code PROCESSED} (l'argent a reellement quitte la
     * tresorerie) et {@code PENDING} (une decision deja prise, pas encore executee) bloquent une
     * nouvelle creation — {@code REJECTED} ne compte jamais comme actif. Voir
     * {@code uq_refunds_payment_active} (migration V20) pour la garantie SQL correspondante :
     * au plus UN remboursement actif a la fois, mais un historique de {@code REJECTED} illimite.
     */
    @Transactional
    public RefundResponse create(UUID paymentId, String reason, UUID actorId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND,
                        "Paiement introuvable : " + paymentId));
        if (payment.getStatus() != PaymentStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.INVALID_PAYMENT_STATE,
                    "Seul un paiement CONFIRMED peut etre rembourse (statut actuel : "
                            + payment.getStatus() + ").");
        }
        // Defense en profondeur : verifie explicitement avant d'inserer, meme si la contrainte
        // SQL uq_refunds_payment_active (V20) est le filet de securite ultime contre une double
        // creation concurrente (meme principe que orders/uq_orders_quote) — scope aux statuts
        // actifs uniquement, un historique REJECTED ne bloque jamais une nouvelle tentative.
        if (refundRepository.existsByPaymentIdAndStatusIn(paymentId, ACTIVE_STATUSES)) {
            throw new BusinessException(ErrorCode.REFUND_ALREADY_EXISTS,
                    "Un remboursement actif (en attente ou deja traite) existe deja pour ce paiement.");
        }

        Instant now = clock.instant();
        Refund refund = new Refund(payment.getOrderId(), paymentId, payment.getReceivedAmountXof(), reason,
                actorId, now);
        Refund saved;
        try {
            saved = refundRepository.saveAndFlush(refund);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.REFUND_ALREADY_EXISTS,
                    "Un remboursement existe deja pour ce paiement.");
        }

        auditService.record(actorId, null, AuditAction.REFUND_CREATED, "Refund", saved.getId().toString(),
                "{\"orderId\":\"" + payment.getOrderId() + "\",\"paymentId\":\"" + paymentId
                        + "\",\"amountXof\":\"" + saved.getAmountXof() + "\"}");
        log.info("Remboursement {} cree pour le paiement {} (ordre {}) par {}", saved.getId(), paymentId,
                payment.getOrderId(), actorId);

        return toResponse(saved);
    }

    /**
     * Enregistre le decaissement XOF reel : seul point de contact avec la tresorerie
     * (voir {@code TreasuryService#refund}). Verrou pessimiste + garde {@link Refund#process}
     * (statut {@code PENDING} requis) : un rejeu concurrent sur le meme remboursement ne peut
     * jamais decaisser deux fois — meme protection que {@code SettlementService#execute}.
     */
    @Transactional
    public RefundResponse process(UUID refundId, String transactionReference, UUID actorId) {
        Refund refund = refundRepository.findByIdForUpdate(refundId).orElseThrow(() -> notFound(refundId));

        Instant now = clock.instant();
        refund.process(transactionReference, actorId, now);
        refundRepository.save(refund);

        treasuryService.refund(Currency.XOF, refund.getAmountXof(), refund.getOrderId(), actorId,
                "Remboursement " + refund.getId() + " (paiement " + refund.getPaymentId() + ")");

        auditService.record(actorId, null, AuditAction.REFUND_PROCESSED, "Refund", refundId.toString(),
                "{\"transactionReference\":\"" + transactionReference + "\"}");
        log.info("Remboursement {} traite par {} (reference {})", refundId, actorId, transactionReference);

        return toResponse(refund);
    }

    @Transactional
    public RefundResponse reject(UUID refundId, String reason, UUID actorId) {
        Refund refund = refundRepository.findByIdForUpdate(refundId).orElseThrow(() -> notFound(refundId));

        Instant now = clock.instant();
        refund.reject(reason, actorId, now);
        refundRepository.save(refund);

        auditService.record(actorId, null, AuditAction.REFUND_REJECTED, "Refund", refundId.toString(),
                "{\"reason\":\"" + reason + "\"}");
        log.info("Remboursement {} rejete par {} : {}", refundId, actorId, reason);

        return toResponse(refund);
    }

    @Transactional(readOnly = true)
    public RefundResponse get(UUID refundId) {
        return toResponse(refundRepository.findById(refundId).orElseThrow(() -> notFound(refundId)));
    }

    /** Le remboursement actif pour ce paiement (PENDING ou PROCESSED), s'il en existe un — jamais un REJECTED historique. */
    @Transactional(readOnly = true)
    public RefundResponse getByPayment(UUID paymentId) {
        return refundRepository.findByPaymentIdAndStatusIn(paymentId, ACTIVE_STATUSES)
                .map(RefundService::toResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_FOUND,
                        "Aucun remboursement actif pour le paiement " + paymentId));
    }

    /**
     * Utilise par {@code SettlementService#execute} : un decaissement CNY ne doit jamais avoir
     * lieu pour un ordre dont le client a deja ete rembourse (voir {@code SettlementService}
     * pour le raisonnement complet du "double decaissement").
     */
    @Transactional(readOnly = true)
    public boolean hasProcessedRefundForOrder(UUID orderId) {
        return refundRepository.existsByOrderIdAndStatus(orderId, RefundStatus.PROCESSED);
    }

    private BusinessException notFound(UUID refundId) {
        return new BusinessException(ErrorCode.REFUND_NOT_FOUND, "Remboursement introuvable : " + refundId);
    }

    private static RefundResponse toResponse(Refund refund) {
        return new RefundResponse(
                refund.getId(), refund.getOrderId(), refund.getPaymentId(), refund.getAmountXof(),
                refund.getStatus(), refund.getReason(), refund.getRejectionReason(),
                refund.getTransactionReference(), refund.getCreatedBy(), refund.getProcessedBy(),
                refund.getCreatedAt(), refund.getProcessedAt());
    }
}
