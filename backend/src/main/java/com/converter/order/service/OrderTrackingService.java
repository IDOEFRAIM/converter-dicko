package com.converter.order.service;

import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.order.domain.Order;
import com.converter.order.domain.OrderStatus;
import com.converter.order.domain.OrderStatusHistory;
import com.converter.order.dto.OrderTrackingResponse;
import com.converter.order.dto.TrackingEvent;
import com.converter.order.dto.TrackingEventCode;
import com.converter.order.repository.OrderRepository;
import com.converter.order.repository.OrderStatusHistoryRepository;
import com.converter.payment.domain.Payment;
import com.converter.payment.domain.PaymentStatus;
import com.converter.payment.repository.PaymentRepository;
import com.converter.refund.domain.Refund;
import com.converter.refund.domain.RefundStatus;
import com.converter.refund.repository.RefundRepository;
import com.converter.security.OwnershipService;
import com.converter.settlement.domain.Settlement;
import com.converter.settlement.domain.SettlementStatus;
import com.converter.settlement.repository.SettlementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Construit la timeline de suivi d'un ordre — <b>projection en lecture seule</b> de l'etat deja
 * persiste, jamais une seconde machine d'etat.
 *
 * <p>La source de verite des transitions reste exclusivement {@code OrderStatusHistory} (append-
 * only, deja ecrite par {@code OrderService} a chaque transition legale). Ce service ne fait que
 * la relire et l'enrichir avec des faits reels de {@code Payment}/{@code Settlement}/
 * {@code Refund} qui n'existent dans aucune valeur de {@code OrderStatus} — jamais l'inverse :
 * aucune ecriture, aucune transition declenchee, aucun evenement fabrique sans donnee reelle
 * pour le justifier. Voir {@link #buildTimeline} pour la regle exacte anti-duplication.
 */
@Service
public class OrderTrackingService {

    /** Seul le remboursement actif (au plus un a la fois) est pertinent — jamais un REJECTED historique, voir RefundService. */
    private static final List<RefundStatus> ACTIVE_REFUND_STATUSES = List.of(RefundStatus.PENDING, RefundStatus.PROCESSED);

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final PaymentRepository paymentRepository;
    private final SettlementRepository settlementRepository;
    private final RefundRepository refundRepository;
    private final OwnershipService ownershipService;

    public OrderTrackingService(OrderRepository orderRepository,
                                OrderStatusHistoryRepository historyRepository,
                                PaymentRepository paymentRepository,
                                SettlementRepository settlementRepository,
                                RefundRepository refundRepository,
                                OwnershipService ownershipService) {
        this.orderRepository = orderRepository;
        this.historyRepository = historyRepository;
        this.paymentRepository = paymentRepository;
        this.settlementRepository = settlementRepository;
        this.refundRepository = refundRepository;
        this.ownershipService = ownershipService;
    }

    /**
     * Charge l'ordre, verifie l'ownership, charge les donnees liees par des lectures ciblees
     * (au plus 5 requetes au total : ordre, historique, paiement, reglement, remboursement —
     * jamais de boucle N+1, voir la javadoc de classe), construit la timeline.
     */
    @Transactional(readOnly = true)
    public OrderTrackingResponse get(UUID orderId, UUID userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Ordre introuvable : " + orderId));
        ownershipService.assertOwnedBy(order.getUserId(), userId, ErrorCode.ORDER_NOT_FOUND,
                "Ordre introuvable : " + orderId);

        List<OrderStatusHistory> history = historyRepository.findByOrderIdOrderByCreatedAtAsc(orderId);
        Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
        Settlement settlement = settlementRepository.findByOrderId(orderId).orElse(null);
        Refund refund = payment == null ? null
                : refundRepository.findByPaymentIdAndStatusIn(payment.getId(), ACTIVE_REFUND_STATUSES).orElse(null);

        List<TrackingEvent> timeline = buildTimeline(history, payment, settlement, refund);

        return new OrderTrackingResponse(order.getId(), order.getStatus(), order.getCreatedAt(),
                order.getCompletedAt(), timeline);
    }

    /**
     * {@code OrderStatusHistory} est la source principale : chaque ligne produit exactement un
     * evenement, jamais duplique. Les enrichissements ne sont ajoutes que lorsque l'information
     * n'existe dans <b>aucune</b> valeur de {@code OrderStatus} :
     * <ul>
     *   <li>{@code PAYMENT_REJECTED} — {@code OrderStatus} n'a pas de valeur dediee (l'ordre
     *       passe directement a {@code REJECTED}), mais {@code Payment.rejectedAt} date reellement
     *       le rejet du paiement lui-meme ;</li>
     *   <li>{@code SETTLEMENT_EXECUTED} — {@code OrderStatus} n'a pas d'etat intermediaire entre
     *       {@code PROCESSING} et {@code COMPLETED} ; {@code Settlement.executedAt} date le
     *       decaissement CNY reel ;</li>
     *   <li>{@code REFUND_PENDING}/{@code REFUND_PROCESSED} — {@code Order.status} ne connait
     *       deliberement aucune valeur {@code REFUNDED} (voir {@code RefundService}) ; le
     *       remboursement reste une verite portee entierement par {@code Refund}, jamais copiee
     *       sur l'ordre.</li>
     * </ul>
     * Tri final par {@code occurredAt} croissant, avec un rang metier fixe comme tie-breaker
     * deterministe (deux ecritures issues d'appels {@code Clock.instant()} distincts, dans la
     * meme transaction ou non, peuvent porter le meme instant — jamais laisse a l'ordre naturel,
     * non garanti, d'une collection).
     */
    private List<TrackingEvent> buildTimeline(List<OrderStatusHistory> history, Payment payment,
                                              Settlement settlement, Refund refund) {
        List<TrackingEvent> events = new ArrayList<>();

        for (OrderStatusHistory entry : history) {
            TrackingEventCode code = codeForTransition(entry.getToStatus());
            events.add(new TrackingEvent(code, entry.getToStatus(), entry.getCreatedAt(), labelFor(code)));
        }

        if (payment != null && payment.getStatus() == PaymentStatus.REJECTED && payment.getRejectedAt() != null) {
            events.add(new TrackingEvent(TrackingEventCode.PAYMENT_REJECTED, null, payment.getRejectedAt(),
                    labelFor(TrackingEventCode.PAYMENT_REJECTED)));
        }

        if (settlement != null && settlement.getStatus() == SettlementStatus.EXECUTED && settlement.getExecutedAt() != null) {
            events.add(new TrackingEvent(TrackingEventCode.SETTLEMENT_EXECUTED, null, settlement.getExecutedAt(),
                    labelFor(TrackingEventCode.SETTLEMENT_EXECUTED)));
        }

        if (refund != null) {
            if (refund.getStatus() == RefundStatus.PROCESSED && refund.getProcessedAt() != null) {
                events.add(new TrackingEvent(TrackingEventCode.REFUND_PROCESSED, null, refund.getProcessedAt(),
                        labelFor(TrackingEventCode.REFUND_PROCESSED)));
            } else if (refund.getStatus() == RefundStatus.PENDING) {
                events.add(new TrackingEvent(TrackingEventCode.REFUND_PENDING, null, refund.getCreatedAt(),
                        labelFor(TrackingEventCode.REFUND_PENDING)));
            }
        }

        events.sort(Comparator.comparing(TrackingEvent::occurredAt)
                .thenComparingInt(event -> businessOrderRank(event.code())));
        return events;
    }

    /** {@code AWAITING_PAYMENT} n'apparait qu'une seule fois dans l'historique d'un ordre : la toute premiere ligne, a la creation. */
    private static TrackingEventCode codeForTransition(OrderStatus toStatus) {
        return switch (toStatus) {
            case AWAITING_PAYMENT -> TrackingEventCode.ORDER_CREATED;
            case PAYMENT_SUBMITTED -> TrackingEventCode.PAYMENT_SUBMITTED;
            case PAYMENT_VERIFIED -> TrackingEventCode.PAYMENT_VERIFIED;
            case PROCESSING -> TrackingEventCode.PROCESSING;
            case COMPLETED -> TrackingEventCode.COMPLETED;
            case CANCELLED -> TrackingEventCode.CANCELLED;
            case REJECTED -> TrackingEventCode.REJECTED;
            case EXPIRED -> TrackingEventCode.EXPIRED;
        };
    }

    /** Tie-breaker deterministe : position dans le recit metier, utilise uniquement a egalite exacte d'{@code occurredAt}. */
    private static int businessOrderRank(TrackingEventCode code) {
        return switch (code) {
            case ORDER_CREATED -> 0;
            case PAYMENT_SUBMITTED -> 10;
            case EXPIRED -> 15;
            case PAYMENT_VERIFIED -> 20;
            case PAYMENT_REJECTED -> 21;
            case REJECTED -> 22;
            case CANCELLED -> 25;
            case PROCESSING -> 30;
            case SETTLEMENT_EXECUTED -> 40;
            case COMPLETED -> 50;
            case REFUND_PENDING -> 60;
            case REFUND_PROCESSED -> 61;
        };
    }

    /**
     * Commodite d'affichage uniquement (section 17 de la specification) — jamais une source de
     * verite, le frontend teste {@code code}. Francais, coherent avec le reste des messages
     * utilisateur du backend (voir {@code NotificationService}, {@code QuoteService}).
     */
    private static String labelFor(TrackingEventCode code) {
        return switch (code) {
            case ORDER_CREATED -> "Ordre cree";
            case PAYMENT_SUBMITTED -> "Paiement declare";
            case PAYMENT_VERIFIED -> "Paiement verifie";
            case PAYMENT_REJECTED -> "Paiement rejete";
            case PROCESSING -> "En cours de traitement";
            case SETTLEMENT_EXECUTED -> "Reglement en Chine execute";
            case COMPLETED -> "Termine";
            case CANCELLED -> "Annule";
            case REJECTED -> "Rejete";
            case EXPIRED -> "Expire";
            case REFUND_PENDING -> "Remboursement en attente";
            case REFUND_PROCESSED -> "Rembourse";
        };
    }
}
