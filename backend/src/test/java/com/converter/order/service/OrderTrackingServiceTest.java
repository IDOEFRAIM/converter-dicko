package com.converter.order.service;

import com.converter.common.exception.BusinessException;
import com.converter.order.domain.Order;
import com.converter.order.domain.OrderStatus;
import com.converter.order.domain.OrderStatusHistory;
import com.converter.order.dto.OrderTrackingResponse;
import com.converter.order.dto.TrackingEvent;
import com.converter.order.dto.TrackingEventCode;
import com.converter.order.repository.OrderRepository;
import com.converter.order.repository.OrderStatusHistoryRepository;
import com.converter.payment.domain.Payment;
import com.converter.payment.domain.PaymentMethod;
import com.converter.payment.repository.PaymentRepository;
import com.converter.security.OwnershipService;
import com.converter.settlement.domain.Settlement;
import com.converter.settlement.repository.SettlementRepository;
import com.converter.order.domain.BeneficiaryType;
import com.converter.refund.domain.Refund;
import com.converter.refund.domain.RefundStatus;
import com.converter.refund.repository.RefundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires purs (aucun contexte Spring) de {@link OrderTrackingService} : verifient
 * precisement le tie-breaker deterministe et la regle anti-duplication, deux proprietes
 * difficiles a forcer via un flux HTTP complet (celui-ci utilise {@code Clock.systemUTC()} —
 * voir {@code ClockConfig} — jamais deux ecritures avec un instant strictement identique en
 * conditions reelles).
 */
@ExtendWith(MockitoExtension.class)
class OrderTrackingServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderStatusHistoryRepository historyRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private RefundRepository refundRepository;

    private OrderTrackingService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new OrderTrackingService(orderRepository, historyRepository, paymentRepository,
                settlementRepository, refundRepository, new OwnershipService());
    }

    private Order orderOwnedBy(UUID owner, OrderStatus targetStatus, Instant createdAt) {
        Order order = new Order("ORD-TEST", owner, UUID.randomUUID(), new BigDecimal("100000.00"),
                new BigDecimal("1176.47"), new BigDecimal("85.000000"), BigDecimal.ZERO,
                new BigDecimal("100000.00"), null, createdAt, createdAt.plusSeconds(43200));
        order.setId(orderId);
        if (targetStatus != OrderStatus.AWAITING_PAYMENT) {
            order.applyStatus(targetStatus, createdAt.plusSeconds(600));
        }
        return order;
    }

    @Test
    void get_orderNotFound_throwsOrderNotFound() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(orderId, userId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode().name()).isEqualTo("ORDER_NOT_FOUND"));
    }

    @Test
    void get_anotherUsersOrder_throwsOrderNotFound() {
        Order order = orderOwnedBy(UUID.randomUUID(), OrderStatus.AWAITING_PAYMENT, Instant.now());
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.get(orderId, userId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode().name()).isEqualTo("ORDER_NOT_FOUND"));
    }

    @Test
    void get_currentStatusComesDirectlyFromOrderEntity_notFromTimeline() {
        Instant t0 = Instant.parse("2026-09-03T10:00:00Z");
        Order order = orderOwnedBy(userId, OrderStatus.PROCESSING, t0);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(historyRepository.findByOrderIdOrderByCreatedAtAsc(orderId)).thenReturn(List.of(
                new OrderStatusHistory(orderId, null, OrderStatus.AWAITING_PAYMENT, userId, null, t0)));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(settlementRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        OrderTrackingResponse response = service.get(orderId, userId);

        assertThat(response.currentStatus()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(response.completedAt()).isNull();
    }

    @Test
    void get_paymentRejectedTiedWithOrderRejected_sortsPaymentRejectedFirst_deterministicTieBreak() {
        Instant t0 = Instant.parse("2026-09-03T10:00:00Z");
        Instant t1 = t0.plusSeconds(60);
        Instant tie = t0.plusSeconds(120);
        Order order = orderOwnedBy(userId, OrderStatus.REJECTED, t0);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(historyRepository.findByOrderIdOrderByCreatedAtAsc(orderId)).thenReturn(List.of(
                new OrderStatusHistory(orderId, null, OrderStatus.AWAITING_PAYMENT, userId, null, t0),
                new OrderStatusHistory(orderId, OrderStatus.AWAITING_PAYMENT, OrderStatus.PAYMENT_SUBMITTED, userId, null, t1),
                // Meme instant que le rejet du paiement ci-dessous : force la collision.
                new OrderStatusHistory(orderId, OrderStatus.PAYMENT_SUBMITTED, OrderStatus.REJECTED, userId, "rejet", tie)));
        Payment payment = new Payment(orderId, PaymentMethod.MOBILE_MONEY, new BigDecimal("100000.00"),
                new BigDecimal("100000.00"), "TX-1", null, null, t1);
        payment.reject(UUID.randomUUID(), "preuve invalide", tie);
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(payment));
        when(settlementRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(refundRepository.findByPaymentIdAndStatusIn(any(), any())).thenReturn(Optional.empty());

        List<TrackingEvent> timeline = service.get(orderId, userId).timeline();

        assertThat(timeline).extracting(TrackingEvent::code).containsExactly(
                TrackingEventCode.ORDER_CREATED, TrackingEventCode.PAYMENT_SUBMITTED,
                TrackingEventCode.PAYMENT_REJECTED, TrackingEventCode.REJECTED);
        assertThat(timeline.get(2).occurredAt()).isEqualTo(timeline.get(3).occurredAt());
    }

    @Test
    void get_settlementExecutedTiedWithCompleted_sortsSettlementExecutedFirst() {
        Instant t0 = Instant.parse("2026-09-03T10:00:00Z");
        Instant tie = t0.plusSeconds(300);
        Order order = orderOwnedBy(userId, OrderStatus.COMPLETED, t0);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(historyRepository.findByOrderIdOrderByCreatedAtAsc(orderId)).thenReturn(List.of(
                new OrderStatusHistory(orderId, null, OrderStatus.AWAITING_PAYMENT, userId, null, t0),
                new OrderStatusHistory(orderId, OrderStatus.PROCESSING, OrderStatus.COMPLETED, userId, null, tie)));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        Settlement settlement = new Settlement(orderId, new BigDecimal("1176.47"), BeneficiaryType.ALIPAY,
                "Zhang San", "zhang.san@example.com", null, null, t0);
        settlement.execute("REF-1", null, UUID.randomUUID(), tie);
        when(settlementRepository.findByOrderId(orderId)).thenReturn(Optional.of(settlement));

        List<TrackingEvent> timeline = service.get(orderId, userId).timeline();

        assertThat(timeline).extracting(TrackingEvent::code).containsExactly(
                TrackingEventCode.ORDER_CREATED, TrackingEventCode.SETTLEMENT_EXECUTED, TrackingEventCode.COMPLETED);
    }

    @Test
    void get_confirmedPaymentAndPendingSettlement_addsNoEnrichmentEvent_noDuplication() {
        // Paiement CONFIRMED (jamais rejete) et reglement PENDING (jamais execute) : aucun des
        // deux ne doit ajouter d'evenement — leur seule information reelle (submittedAt/
        // confirmedAt, createdAt de settlement) est deja portee par OrderStatusHistory.
        Instant t0 = Instant.parse("2026-09-03T10:00:00Z");
        Order order = orderOwnedBy(userId, OrderStatus.PROCESSING, t0);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(historyRepository.findByOrderIdOrderByCreatedAtAsc(orderId)).thenReturn(List.of(
                new OrderStatusHistory(orderId, null, OrderStatus.AWAITING_PAYMENT, userId, null, t0),
                new OrderStatusHistory(orderId, OrderStatus.AWAITING_PAYMENT, OrderStatus.PAYMENT_SUBMITTED, userId, null, t0.plusSeconds(60)),
                new OrderStatusHistory(orderId, OrderStatus.PAYMENT_SUBMITTED, OrderStatus.PAYMENT_VERIFIED, userId, null, t0.plusSeconds(120)),
                new OrderStatusHistory(orderId, OrderStatus.PAYMENT_VERIFIED, OrderStatus.PROCESSING, userId, null, t0.plusSeconds(180))));
        Payment payment = new Payment(orderId, PaymentMethod.MOBILE_MONEY, new BigDecimal("100000.00"),
                new BigDecimal("100000.00"), "TX-2", null, null, t0.plusSeconds(60));
        payment.confirm(UUID.randomUUID(), t0.plusSeconds(120));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(payment));
        Settlement settlement = new Settlement(orderId, new BigDecimal("1176.47"), BeneficiaryType.ALIPAY,
                "Zhang San", "zhang.san@example.com", null, null, t0.plusSeconds(180));
        when(settlementRepository.findByOrderId(orderId)).thenReturn(Optional.of(settlement));
        when(refundRepository.findByPaymentIdAndStatusIn(any(), any())).thenReturn(Optional.empty());

        List<TrackingEvent> timeline = service.get(orderId, userId).timeline();

        assertThat(timeline).hasSize(4);
        assertThat(timeline).extracting(TrackingEvent::code).containsExactly(
                TrackingEventCode.ORDER_CREATED, TrackingEventCode.PAYMENT_SUBMITTED,
                TrackingEventCode.PAYMENT_VERIFIED, TrackingEventCode.PROCESSING);
    }

    @Test
    void get_pendingRefund_addsRefundPendingEvent_notProcessed() {
        Instant t0 = Instant.parse("2026-09-03T10:00:00Z");
        Order order = orderOwnedBy(userId, OrderStatus.PROCESSING, t0);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(historyRepository.findByOrderIdOrderByCreatedAtAsc(orderId)).thenReturn(List.of(
                new OrderStatusHistory(orderId, null, OrderStatus.AWAITING_PAYMENT, userId, null, t0)));
        Payment payment = new Payment(orderId, PaymentMethod.MOBILE_MONEY, new BigDecimal("100000.00"),
                new BigDecimal("100000.00"), "TX-3", null, null, t0);
        payment.confirm(UUID.randomUUID(), t0.plusSeconds(60));
        payment.setId(UUID.randomUUID());
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(payment));
        when(settlementRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        Refund refund = new Refund(orderId, payment.getId(), payment.getReceivedAmountXof(), "erreur",
                UUID.randomUUID(), t0.plusSeconds(300));
        when(refundRepository.findByPaymentIdAndStatusIn(payment.getId(), List.of(RefundStatus.PENDING, RefundStatus.PROCESSED)))
                .thenReturn(Optional.of(refund));

        List<TrackingEvent> timeline = service.get(orderId, userId).timeline();

        assertThat(timeline).extracting(TrackingEvent::code).contains(TrackingEventCode.REFUND_PENDING);
        assertThat(timeline).extracting(TrackingEvent::code).doesNotContain(TrackingEventCode.REFUND_PROCESSED);
    }

    @Test
    void get_processedRefund_addsRefundProcessedEvent_notPending() {
        Instant t0 = Instant.parse("2026-09-03T10:00:00Z");
        Order order = orderOwnedBy(userId, OrderStatus.PROCESSING, t0);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(historyRepository.findByOrderIdOrderByCreatedAtAsc(orderId)).thenReturn(List.of(
                new OrderStatusHistory(orderId, null, OrderStatus.AWAITING_PAYMENT, userId, null, t0)));
        Payment payment = new Payment(orderId, PaymentMethod.MOBILE_MONEY, new BigDecimal("100000.00"),
                new BigDecimal("100000.00"), "TX-4", null, null, t0);
        payment.confirm(UUID.randomUUID(), t0.plusSeconds(60));
        payment.setId(UUID.randomUUID());
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(payment));
        when(settlementRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        Refund refund = new Refund(orderId, payment.getId(), payment.getReceivedAmountXof(), "erreur",
                UUID.randomUUID(), t0.plusSeconds(300));
        refund.process("REFUND-TX-1", UUID.randomUUID(), t0.plusSeconds(400));
        when(refundRepository.findByPaymentIdAndStatusIn(payment.getId(), List.of(RefundStatus.PENDING, RefundStatus.PROCESSED)))
                .thenReturn(Optional.of(refund));

        List<TrackingEvent> timeline = service.get(orderId, userId).timeline();

        assertThat(timeline).extracting(TrackingEvent::code).contains(TrackingEventCode.REFUND_PROCESSED);
        assertThat(timeline).extracting(TrackingEvent::code).doesNotContain(TrackingEventCode.REFUND_PENDING);
    }
}
