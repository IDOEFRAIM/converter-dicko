package com.converter.order.service;

import com.converter.common.exception.BusinessException;
import com.converter.order.domain.OrderStatus;
import com.converter.order.dto.OrderDetailResponse;
import com.converter.order.dto.OrderTrackingResponse;
import com.converter.order.dto.TrackingEvent;
import com.converter.order.dto.TrackingEventCode;
import com.converter.payment.dto.PaymentResponse;
import com.converter.quote.dto.QuoteResponse;
import com.converter.refund.dto.RefundResponse;
import com.converter.settlement.dto.SettlementResponse;
import com.converter.support.AbstractOrderPipelineIT;
import com.converter.user.domain.RoleCode;
import com.converter.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 3 — timeline de suivi, exercee a travers de vrais parcours de la machine d'etat
 * existante (jamais une seconde machine d'etat : voir {@code OrderTrackingService}).
 */
class OrderTrackingFlowIT extends AbstractOrderPipelineIT {

    @Autowired
    private OrderTrackingService orderTrackingService;

    @Autowired
    private OrderService orderService;

    private String setUpAdminWithLiquidity() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        return admin;
    }

    // ---- 1 : AWAITING_PAYMENT ----

    @Test
    void tracking_awaitingPayment_showsOnlyOrderCreated() {
        setUpAdminWithLiquidity();
        User userEntity = createUser(RoleCode.USER);
        String user = tokenFor(userEntity);
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());

        OrderTrackingResponse tracking = orderTrackingService.get(order.id(), userEntity.getId());

        assertThat(tracking.orderId()).isEqualTo(order.id());
        assertThat(tracking.currentStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
        assertThat(tracking.completedAt()).isNull();
        assertThat(tracking.timeline()).extracting(TrackingEvent::code).containsExactly(TrackingEventCode.ORDER_CREATED);
        // order.createdAt() (reponse HTTP de creation, precision nanoseconde en memoire) vs
        // tracking.createdAt() (relu depuis Postgres, TIMESTAMPTZ tronque a la microseconde) :
        // comparer les deux representations en egalite stricte produirait un faux echec sans
        // rapport avec le tracking lui-meme (meme piege documente dans NotificationServiceIT).
        assertThat(tracking.timeline().get(0).occurredAt()).isEqualTo(tracking.createdAt());
    }

    // ---- 2 : PAYMENT_SUBMITTED ----

    @Test
    void tracking_paymentSubmitted_addsPaymentSubmittedEvent() {
        setUpAdminWithLiquidity();
        User userEntity = createUser(RoleCode.USER);
        String user = tokenFor(userEntity);
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        submitPayment(user, order.id(), "100000");

        OrderTrackingResponse tracking = orderTrackingService.get(order.id(), userEntity.getId());

        assertThat(tracking.currentStatus()).isEqualTo(OrderStatus.PAYMENT_SUBMITTED);
        assertThat(tracking.timeline()).extracting(TrackingEvent::code).containsExactly(
                TrackingEventCode.ORDER_CREATED, TrackingEventCode.PAYMENT_SUBMITTED);
    }

    // ---- 3 : PAYMENT_VERIFIED ----

    @Test
    void tracking_paymentVerified_addsPaymentVerifiedEvent() {
        String admin = setUpAdminWithLiquidity();
        User userEntity = createUser(RoleCode.USER);
        String user = tokenFor(userEntity);
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        PaymentResponse payment = submitPayment(user, order.id(), "100000");
        uploadProof(user, payment.id());
        confirmPayment(admin, payment.id());

        OrderTrackingResponse tracking = orderTrackingService.get(order.id(), userEntity.getId());

        assertThat(tracking.currentStatus()).isEqualTo(OrderStatus.PAYMENT_VERIFIED);
        assertThat(tracking.timeline()).extracting(TrackingEvent::code).containsExactly(
                TrackingEventCode.ORDER_CREATED, TrackingEventCode.PAYMENT_SUBMITTED, TrackingEventCode.PAYMENT_VERIFIED);
    }

    // ---- 4 : PROCESSING ----

    @Test
    void tracking_processing_addsProcessingEvent() {
        String admin = setUpAdminWithLiquidity();
        User userEntity = createUser(RoleCode.USER);
        String user = tokenFor(userEntity);
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        PaymentResponse payment = submitPayment(user, order.id(), "100000");
        uploadProof(user, payment.id());
        confirmPayment(admin, payment.id());
        createSettlement(admin, order.id());

        OrderTrackingResponse tracking = orderTrackingService.get(order.id(), userEntity.getId());

        assertThat(tracking.currentStatus()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(tracking.timeline()).extracting(TrackingEvent::code).containsExactly(
                TrackingEventCode.ORDER_CREATED, TrackingEventCode.PAYMENT_SUBMITTED,
                TrackingEventCode.PAYMENT_VERIFIED, TrackingEventCode.PROCESSING);
    }

    // ---- 5 / 9 / 13 / 14 / 15 : COMPLETED, avec enrichissement SETTLEMENT_EXECUTED, tri chronologique, pas de duplication ----

    @Test
    void tracking_completed_addsSettlementExecutedBeforeCompleted_chronologicallySortedAndNotDuplicated() {
        String admin = setUpAdminWithLiquidity();
        User userEntity = createUser(RoleCode.USER);
        String user = tokenFor(userEntity);
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        PaymentResponse payment = submitPayment(user, order.id(), "100000");
        uploadProof(user, payment.id());
        confirmPayment(admin, payment.id());
        SettlementResponse settlement = createSettlement(admin, order.id());
        uploadSettlementProof(admin, settlement.id());
        executeSettlement(admin, settlement.id(), "SETTLE-REF-" + UUID.randomUUID());

        OrderTrackingResponse tracking = orderTrackingService.get(order.id(), userEntity.getId());

        assertThat(tracking.currentStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(tracking.completedAt()).isNotNull();
        List<TrackingEvent> timeline = tracking.timeline();
        assertThat(timeline).hasSize(6);
        assertThat(timeline).extracting(TrackingEvent::code).containsExactly(
                TrackingEventCode.ORDER_CREATED, TrackingEventCode.PAYMENT_SUBMITTED,
                TrackingEventCode.PAYMENT_VERIFIED, TrackingEventCode.PROCESSING,
                TrackingEventCode.SETTLEMENT_EXECUTED, TrackingEventCode.COMPLETED);
        // Tri chronologique strict : chaque occurredAt est >= au precedent.
        for (int i = 1; i < timeline.size(); i++) {
            assertThat(timeline.get(i).occurredAt()).isAfterOrEqualTo(timeline.get(i - 1).occurredAt());
        }
    }

    // ---- 6 : CANCELLED ----

    @Test
    void tracking_cancelled_stopsCleanlyWithNoFabricatedFutureEvents() {
        setUpAdminWithLiquidity();
        User userEntity = createUser(RoleCode.USER);
        String user = tokenFor(userEntity);
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        orderService.cancel(order.id(), userEntity.getId(), "changement d'avis");

        OrderTrackingResponse tracking = orderTrackingService.get(order.id(), userEntity.getId());

        assertThat(tracking.currentStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(tracking.timeline()).extracting(TrackingEvent::code).containsExactly(
                TrackingEventCode.ORDER_CREATED, TrackingEventCode.CANCELLED);
    }

    // ---- 7 : REJECTED, avec enrichissement PAYMENT_REJECTED ----

    @Test
    void tracking_rejected_addsPaymentRejectedBeforeRejected() {
        String admin = setUpAdminWithLiquidity();
        User userEntity = createUser(RoleCode.USER);
        String user = tokenFor(userEntity);
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        PaymentResponse payment = submitPayment(user, order.id(), "100000");
        rejectPayment(admin, payment.id(), "preuve illisible");

        OrderTrackingResponse tracking = orderTrackingService.get(order.id(), userEntity.getId());

        assertThat(tracking.currentStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(tracking.timeline()).extracting(TrackingEvent::code).containsExactly(
                TrackingEventCode.ORDER_CREATED, TrackingEventCode.PAYMENT_SUBMITTED,
                TrackingEventCode.PAYMENT_REJECTED, TrackingEventCode.REJECTED);
    }

    // ---- 8 : EXPIRED ----

    @Test
    void tracking_expired_stopsCleanlyAfterExpiration() {
        setUpAdminWithLiquidity();
        User userEntity = createUser(RoleCode.USER);
        String user = tokenFor(userEntity);
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());

        boolean expired = orderService.expireIfOverdue(order.id(), Instant.now().plusSeconds(365L * 24 * 3600));
        assertThat(expired).isTrue();

        OrderTrackingResponse tracking = orderTrackingService.get(order.id(), userEntity.getId());

        assertThat(tracking.currentStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(tracking.timeline()).extracting(TrackingEvent::code).containsExactly(
                TrackingEventCode.ORDER_CREATED, TrackingEventCode.EXPIRED);
    }

    // ---- 10 / 11 : Refund pending / processed ----

    @Test
    void tracking_refundPending_addsRefundPendingEvent() {
        String admin = setUpAdminWithLiquidity();
        User userEntity = createUser(RoleCode.USER);
        String user = tokenFor(userEntity);
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        PaymentResponse payment = submitPayment(user, order.id(), "100000");
        uploadProof(user, payment.id());
        confirmPayment(admin, payment.id());
        createRefund(admin, payment.id(), "erreur de commande");

        OrderTrackingResponse tracking = orderTrackingService.get(order.id(), userEntity.getId());

        assertThat(tracking.timeline()).extracting(TrackingEvent::code).contains(TrackingEventCode.REFUND_PENDING);
        assertThat(tracking.timeline()).extracting(TrackingEvent::code).doesNotContain(TrackingEventCode.REFUND_PROCESSED);
        // Order.status n'est jamais devenu un pseudo-statut REFUNDED — voir OrderStatus (inchange).
        assertThat(tracking.currentStatus()).isEqualTo(OrderStatus.PAYMENT_VERIFIED);
    }

    @Test
    void tracking_refundProcessed_addsRefundProcessedEvent() {
        String admin = setUpAdminWithLiquidity();
        User userEntity = createUser(RoleCode.USER);
        String user = tokenFor(userEntity);
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());
        PaymentResponse payment = submitPayment(user, order.id(), "100000");
        uploadProof(user, payment.id());
        confirmPayment(admin, payment.id());
        RefundResponse refund = createRefund(admin, payment.id(), "erreur de commande");
        processRefund(admin, refund.id(), "REFUND-TX-" + UUID.randomUUID());

        OrderTrackingResponse tracking = orderTrackingService.get(order.id(), userEntity.getId());

        assertThat(tracking.timeline()).extracting(TrackingEvent::code).contains(TrackingEventCode.REFUND_PROCESSED);
        assertThat(tracking.timeline()).extracting(TrackingEvent::code).doesNotContain(TrackingEventCode.REFUND_PENDING);
    }

    // ---- 16 : ownership incorrect, niveau service ----

    @Test
    void tracking_anotherUsersOrder_throwsOrderNotFound_atServiceLevel() {
        setUpAdminWithLiquidity();
        User owner = createUser(RoleCode.USER);
        String ownerToken = tokenFor(owner);
        QuoteResponse quote = createAcceptedQuote(ownerToken, "100000");
        OrderDetailResponse order = createOrder(ownerToken, quote.id(), alipayBeneficiary());
        UUID intruderId = createUser(RoleCode.USER).getId();

        assertThatThrownBy(() -> orderTrackingService.get(order.id(), intruderId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode().name()).isEqualTo("ORDER_NOT_FOUND"));
    }

    // ---- 17 : ordre inexistant, niveau service ----

    @Test
    void tracking_unknownOrder_throwsOrderNotFound_atServiceLevel() {
        UUID randomUserId = createUser(RoleCode.USER).getId();

        assertThatThrownBy(() -> orderTrackingService.get(UUID.randomUUID(), randomUserId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode().name()).isEqualTo("ORDER_NOT_FOUND"));
    }
}
