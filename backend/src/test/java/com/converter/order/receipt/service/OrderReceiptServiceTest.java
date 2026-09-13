package com.converter.order.receipt.service;

import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.order.domain.Beneficiary;
import com.converter.order.domain.BeneficiaryType;
import com.converter.order.domain.Order;
import com.converter.order.domain.OrderStatus;
import com.converter.order.receipt.model.TransferReceiptModel;
import com.converter.order.receipt.pdf.ReceiptPdfGenerator;
import com.converter.order.repository.BeneficiaryRepository;
import com.converter.order.repository.OrderRepository;
import com.converter.payment.domain.Payment;
import com.converter.payment.domain.PaymentMethod;
import com.converter.payment.repository.PaymentRepository;
import com.converter.rate.provider.RateProvider;
import com.converter.refund.repository.RefundRepository;
import com.converter.security.OwnershipService;
import com.converter.settlement.repository.SettlementRepository;
import com.converter.supplier.domain.Purpose;
import com.converter.user.domain.User;
import com.converter.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@code OrderReceiptService}, repositories entierement simules — meme style
 * que {@code RateAlertServiceTest}. Verifie le mapping complet vers {@link TransferReceiptModel}
 * (section 32, items 1-10) via un {@link ArgumentCaptor} sur l'appel a {@code ReceiptPdfGenerator},
 * et les deux garde-fous (ownership, statut) sans jamais atteindre le generateur PDF.
 */
@ExtendWith(MockitoExtension.class)
class OrderReceiptServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private BeneficiaryRepository beneficiaryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private SettlementRepository settlementRepository;
    @Mock
    private RefundRepository refundRepository;
    @Mock
    private OwnershipService ownershipService;
    @Mock
    private ReceiptPdfGenerator pdfGenerator;

    private OrderReceiptService service;

    @BeforeEach
    void setUp() {
        service = new OrderReceiptService(orderRepository, beneficiaryRepository, userRepository, paymentRepository,
                settlementRepository, refundRepository, ownershipService, pdfGenerator);
    }

    private Order completedOrder(UUID userId) {
        Order order = new Order("ORD-2026-000456", userId, UUID.randomUUID(),
                new BigDecimal("1000000.00"), new BigDecimal("11734.56"), new BigDecimal("84.200000"),
                new BigDecimal("12500.00"), new BigDecimal("987500.00"), "note",
                Instant.parse("2026-09-01T10:00:00Z"), Instant.parse("2026-09-01T22:00:00Z"),
                null, Purpose.IMPORT_GOODS, "Materiel electronique");
        order.setId(UUID.randomUUID());
        order.applyStatus(OrderStatus.PAYMENT_SUBMITTED, Instant.now());
        order.applyStatus(OrderStatus.PAYMENT_VERIFIED, Instant.now());
        order.applyStatus(OrderStatus.PROCESSING, Instant.now());
        order.applyStatus(OrderStatus.COMPLETED, Instant.parse("2026-09-02T15:30:00Z"));
        return order;
    }

    @Test
    void generate_orderNotCompleted_throwsInvalidOrderState_neverCallsGenerator() {
        UUID userId = UUID.randomUUID();
        Order order = new Order("ORD-2026-000789", userId, UUID.randomUUID(),
                new BigDecimal("1000000.00"), new BigDecimal("11734.56"), new BigDecimal("84.200000"),
                new BigDecimal("12500.00"), new BigDecimal("987500.00"), null,
                Instant.now(), Instant.now().plusSeconds(3600));
        order.setId(UUID.randomUUID());
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.generate(order.getId(), userId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo(ErrorCode.INVALID_ORDER_STATE));

        verify(pdfGenerator, never()).generate(any());
        verify(beneficiaryRepository, never()).findByOrderId(any());
    }

    @Test
    void generate_anotherUsersOrder_throwsNotFound_neverCallsGenerator() {
        UUID ownerId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        Order order = completedOrder(ownerId);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        doThrow(new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Ordre introuvable"))
                .when(ownershipService).assertOwnedBy(ownerId, strangerId, ErrorCode.ORDER_NOT_FOUND,
                        "Ordre introuvable : " + order.getId());

        assertThatThrownBy(() -> service.generate(order.getId(), strangerId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo(ErrorCode.ORDER_NOT_FOUND));

        verify(pdfGenerator, never()).generate(any());
    }

    @Test
    void generate_unknownOrder_throwsNotFound() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generate(orderId, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo(ErrorCode.ORDER_NOT_FOUND));
    }

    @Test
    void generate_completedOrder_mapsEveryFieldFromPersistedSnapshots_neverRecalculated() {
        UUID userId = UUID.randomUUID();
        Order order = completedOrder(userId);
        User user = new User("+22507000000", "hash", "Ido", "Efraim");
        Beneficiary beneficiary = new Beneficiary(order.getId(), BeneficiaryType.CHINESE_BANK_ACCOUNT, "Zhang San",
                "6222000000001111", "Bank of China", "Shanghai Branch", null, null, null, null,
                order.getCreatedAt());
        Payment payment = new Payment(order.getId(), PaymentMethod.MOBILE_MONEY, order.getAmountXof(),
                order.getAmountXof(), "MM-PAY-REF-42", "+2250700000000", "Payeur Test",
                Instant.parse("2026-09-01T11:00:00Z"));
        payment.confirm(UUID.randomUUID(), Instant.parse("2026-09-01T12:00:00Z"));

        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(beneficiaryRepository.findByOrderId(order.getId())).thenReturn(Optional.of(beneficiary));
        when(paymentRepository.findByOrderId(order.getId())).thenReturn(Optional.of(payment));
        when(settlementRepository.findByOrderId(order.getId())).thenReturn(Optional.empty());
        when(refundRepository.findFirstByPaymentIdOrderByCreatedAtDesc(payment.getId())).thenReturn(Optional.empty());
        when(pdfGenerator.generate(any())).thenReturn(new byte[]{1, 2, 3});

        var document = service.generate(order.getId(), userId);

        ArgumentCaptor<TransferReceiptModel> captor = ArgumentCaptor.forClass(TransferReceiptModel.class);
        verify(pdfGenerator).generate(captor.capture());
        TransferReceiptModel model = captor.getValue();

        assertThat(model.orderId()).isEqualTo(order.getId());
        assertThat(model.transactionReference()).isEqualTo("ORD-2026-000456");
        assertThat(model.orderStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(model.customerName()).isEqualTo("Ido Efraim");
        assertThat(model.amountXof()).isEqualByComparingTo("1000000.00");
        assertThat(model.feeXof()).isEqualByComparingTo("12500.00");
        assertThat(model.netAmountXof()).isEqualByComparingTo("987500.00");
        assertThat(model.customerRate()).isEqualByComparingTo("84.200000");
        assertThat(model.amountCny()).isEqualByComparingTo("11734.56");
        assertThat(model.currencyPair()).isEqualTo(RateProvider.DEFAULT_CURRENCY_PAIR);
        assertThat(model.purpose()).isEqualTo(Purpose.IMPORT_GOODS);
        assertThat(model.purposeDetails()).isEqualTo("Materiel electronique");
        assertThat(model.paymentReference()).isEqualTo("MM-PAY-REF-42");
        assertThat(model.paymentVerifiedAt()).isEqualTo(Instant.parse("2026-09-01T12:00:00Z"));
        assertThat(model.settlementReference()).isNull();
        assertThat(model.settlementExecutedAt()).isNull();
        assertThat(model.refund()).isNull();

        assertThat(model.beneficiary().fullName()).isEqualTo("Zhang San");
        assertThat(model.beneficiary().bankName()).isEqualTo("Bank of China");
        // Jamais l'identifiant en clair dans le modele transmis au generateur.
        assertThat(model.beneficiary().maskedIdentifier()).isEqualTo("******1111");
        assertThat(model.beneficiary().maskedIdentifier()).doesNotContain("6222000000001111");

        assertThat(document.content()).containsExactly(1, 2, 3);
        assertThat(document.fileName()).isEqualTo("transaction-ORD-2026-000456.pdf");
    }

    @Test
    void mask_shortIdentifier_isFullyMasked() {
        assertThat(OrderReceiptService.mask("123")).isEqualTo("******");
        assertThat(OrderReceiptService.mask(null)).isEqualTo("******");
    }

    @Test
    void mask_normalIdentifier_keepsOnlyLastFourCharacters() {
        assertThat(OrderReceiptService.mask("6222000000001111")).isEqualTo("******1111");
        assertThat(OrderReceiptService.mask("zhang.san@example.com")).isEqualTo("******.com");
    }
}
