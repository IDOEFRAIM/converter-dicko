package com.converter.transfi.service;

import com.converter.audit.service.AuditService;
import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.config.props.TransFiProperties;
import com.converter.ledger.service.LedgerService;
import com.converter.order.domain.Beneficiary;
import com.converter.order.domain.BeneficiaryType;
import com.converter.order.domain.Order;
import com.converter.order.domain.OrderStatus;
import com.converter.order.service.OrderService;
import com.converter.transfi.client.TransFiClient;
import com.converter.transfi.client.TransFiOrderResult;
import com.converter.transfi.domain.TransfiOrder;
import com.converter.transfi.domain.TransfiOrderDirection;
import com.converter.transfi.domain.TransfiOrderStatus;
import com.converter.transfi.repository.TransfiOrderRepository;
import com.converter.transfi.repository.TransfiWebhookEventRepository;
import com.converter.treasury.domain.Currency;
import com.converter.treasury.service.TreasuryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link TransfiOrchestrationService} : {@link TransFiClient} entierement
 * simule (voir sa Javadoc), aucun appel reseau reel n'est jamais fait en test. Couvre les
 * garde-fous Phase 1 (jamais declenche automatiquement, jamais un second payin, jamais un
 * decaissement CNY en double) — voir {@code docs/TRANSFI_INTEGRATION.md}.
 */
@ExtendWith(MockitoExtension.class)
class TransfiOrchestrationServiceTest {

    @Mock
    private TransFiClient client;

    @Mock
    private TransfiOrderRepository repository;

    @Mock
    private OrderService orderService;

    @Mock
    private TreasuryService treasuryService;

    @Mock
    private LedgerService ledgerService;

    @Mock
    private AuditService auditService;

    @Mock
    private TransfiWebhookEventRepository webhookEventRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-25T12:00:00Z"), ZoneOffset.UTC);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private TransfiOrchestrationService service;

    @BeforeEach
    void setUp() {
        TransFiProperties activeProperties = new TransFiProperties(true, "https://api.transfi.test", "client-id",
                "client-secret", "webhook-secret");
        service = new TransfiOrchestrationService(activeProperties, client, repository, orderService,
                treasuryService, ledgerService, auditService, webhookEventRepository, objectMapper, clock);
    }

    @Test
    void routePayin_whenIntegrationNotActive_throwsTransfiUnavailable() {
        TransFiProperties inactive = new TransFiProperties(false, null, null, null, null);
        TransfiOrchestrationService inactiveService = new TransfiOrchestrationService(inactive, client, repository,
                orderService, treasuryService, ledgerService, auditService, webhookEventRepository, objectMapper, clock);
        UUID orderId = UUID.randomUUID();

        assertThatThrownBy(() -> inactiveService.routePayin(orderId, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo(ErrorCode.TRANSFI_UNAVAILABLE));
        verify(client, never()).createOrder(any());
    }

    @Test
    void routePayin_whenOrderNotAwaitingPayment_throwsInvalidOrderState() {
        UUID orderId = UUID.randomUUID();
        Order order = orderOf(orderId, OrderStatus.PAYMENT_SUBMITTED);
        when(orderService.getEntityOrThrow(orderId)).thenReturn(order);

        assertThatThrownBy(() -> service.routePayin(orderId, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo(ErrorCode.INVALID_ORDER_STATE));
        verify(client, never()).createOrder(any());
    }

    @Test
    void routePayin_whenAlreadyRouted_throwsAlreadyRouted() {
        UUID orderId = UUID.randomUUID();
        Order order = orderOf(orderId, OrderStatus.AWAITING_PAYMENT);
        when(orderService.getEntityOrThrow(orderId)).thenReturn(order);
        when(repository.findByOrderIdAndDirection(orderId, TransfiOrderDirection.PAYIN))
                .thenReturn(Optional.of(new TransfiOrder(orderId, TransfiOrderDirection.PAYIN, clock.instant())));

        assertThatThrownBy(() -> service.routePayin(orderId, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode())
                        .isEqualTo(ErrorCode.TRANSFI_ORDER_ALREADY_ROUTED));
        verify(client, never()).createOrder(any());
    }

    @Test
    void routePayin_withValidOrder_createsPayinAndNeverMovesOrderStatus() {
        UUID orderId = UUID.randomUUID();
        Order order = orderOf(orderId, OrderStatus.AWAITING_PAYMENT);
        when(orderService.getEntityOrThrow(orderId)).thenReturn(order);
        when(repository.findByOrderIdAndDirection(orderId, TransfiOrderDirection.PAYIN)).thenReturn(Optional.empty());
        when(client.createOrder(any())).thenReturn(new TransFiOrderResult("provider-1", "created",
                "https://pay.transfi.test/x", "{}"));
        when(repository.saveAndFlush(any(TransfiOrder.class))).thenAnswer(invocation -> {
            TransfiOrder arg = invocation.getArgument(0);
            arg.setId(UUID.randomUUID());
            return arg;
        });

        TransfiOrder result = service.routePayin(orderId, UUID.randomUUID());

        assertThat(result.getProviderOrderId()).isEqualTo("provider-1");
        assertThat(result.getPayUrl()).isEqualTo("https://pay.transfi.test/x");
        verify(orderService, never()).transitionToPaymentVerified(any(), any());
        verify(orderService, never()).transitionToProcessing(any(), any());
    }

    @Test
    void handleWebhook_withDuplicateEventId_isIgnoredWithoutProcessing() {
        when(webhookEventRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uq_transfi_webhook_events_provider_event_id"));

        service.handleWebhook("evt-1", "payin", "provider-1", "success", "{}");

        verify(repository, never()).findByProviderOrderIdForUpdate(anyString());
    }

    @Test
    void handleWebhook_payinSuccess_verifiesPaymentThenMovesToProcessingThenCreatesPayout() {
        UUID orderId = UUID.randomUUID();
        TransfiOrder payin = new TransfiOrder(orderId, TransfiOrderDirection.PAYIN, clock.instant());
        when(repository.findByProviderOrderIdForUpdate("provider-payin")).thenReturn(Optional.of(payin));
        Order order = orderOf(orderId, OrderStatus.PAYMENT_VERIFIED);
        when(orderService.getEntityOrThrow(orderId)).thenReturn(order);
        when(orderService.getBeneficiaryOrThrow(orderId)).thenReturn(beneficiaryOf(orderId));
        when(client.createOrder(any())).thenReturn(new TransFiOrderResult("provider-payout", "created", null, "{}"));
        when(repository.saveAndFlush(any(TransfiOrder.class))).thenAnswer(invocation -> {
            TransfiOrder arg = invocation.getArgument(0);
            arg.setId(UUID.randomUUID());
            return arg;
        });

        service.handleWebhook("evt-2", "payin", "provider-payin", "success", "{\"status\":\"success\"}");

        verify(orderService).transitionToPaymentVerified(orderId, null);
        verify(orderService).transitionToProcessing(orderId, null);
        verify(client).createOrder(any());
        verify(orderService, never()).transitionToCompleted(any(), any());
    }

    @Test
    void handleWebhook_payinFailure_rejectsOrderWithoutTouchingTreasury() {
        UUID orderId = UUID.randomUUID();
        TransfiOrder payin = new TransfiOrder(orderId, TransfiOrderDirection.PAYIN, clock.instant());
        when(repository.findByProviderOrderIdForUpdate("provider-payin")).thenReturn(Optional.of(payin));

        service.handleWebhook("evt-3", "payin", "provider-payin", "failed", "{\"status\":\"failed\"}");

        verify(orderService).transitionToRejected(eq(orderId), eq(null), anyString());
        verify(treasuryService, never()).consume(any(), any(), any(), any());
    }

    @Test
    void handleWebhook_payoutSuccess_consumesTreasuryOnceThenCompletesOrder() {
        UUID orderId = UUID.randomUUID();
        TransfiOrder payout = new TransfiOrder(orderId, TransfiOrderDirection.PAYOUT, clock.instant());
        when(repository.findByProviderOrderIdForUpdate("provider-payout")).thenReturn(Optional.of(payout));
        Order order = orderOf(orderId, OrderStatus.PROCESSING);
        when(orderService.getEntityOrThrow(orderId)).thenReturn(order);

        service.handleWebhook("evt-4", "payout", "provider-payout", "success", "{\"status\":\"success\"}");

        verify(treasuryService, times(1)).consume(Currency.CNY, order.getAmountCny(), orderId, null);
        verify(orderService).transitionToCompleted(orderId, null);
    }

    @Test
    void handleWebhook_payoutFailure_neverCancelsOrderOrTouchesTreasury() {
        UUID orderId = UUID.randomUUID();
        TransfiOrder payout = new TransfiOrder(orderId, TransfiOrderDirection.PAYOUT, clock.instant());
        when(repository.findByProviderOrderIdForUpdate("provider-payout")).thenReturn(Optional.of(payout));

        service.handleWebhook("evt-5", "payout", "provider-payout", "failed", "{\"status\":\"failed\"}");

        verify(treasuryService, never()).consume(any(), any(), any(), any());
        verify(orderService, never()).transitionToCompleted(any(), any());
        verify(orderService, never()).transitionToRejected(any(), any(), anyString());
    }

    @Test
    void handleWebhook_forUnknownProviderOrderId_isIgnoredSilently() {
        when(repository.findByProviderOrderIdForUpdate("unknown")).thenReturn(Optional.empty());

        service.handleWebhook("evt-6", "payin", "unknown", "success", "{}");

        verify(orderService, never()).transitionToPaymentVerified(any(), any());
        verify(orderService, never()).transitionToCompleted(any(), any());
    }

    private static Order orderOf(UUID orderId, OrderStatus status) {
        Order order = new Order("REF-" + orderId, UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100000"),
                new BigDecimal("1000"), new BigDecimal("85"), new BigDecimal("1500"), new BigDecimal("98500"), null,
                Instant.parse("2026-09-25T10:00:00Z"), Instant.parse("2026-09-26T10:00:00Z"));
        order.setId(orderId);
        order.applyStatus(status, Instant.parse("2026-09-25T11:00:00Z"));
        return order;
    }

    private static Beneficiary beneficiaryOf(UUID orderId) {
        return new Beneficiary(orderId, BeneficiaryType.ALIPAY, "Beneficiary Name", "alipay-id-1",
                null, null, null, null, null, null, Instant.parse("2026-09-25T09:00:00Z"));
    }

}
