package com.converter.supplier.service;

import com.converter.common.exception.BusinessException;
import com.converter.order.domain.BeneficiaryType;
import com.converter.order.dto.OrderDetailResponse;
import com.converter.order.dto.OrderTrackingResponse;
import com.converter.order.dto.TrackingEvent;
import com.converter.order.service.OrderTrackingService;
import com.converter.quote.domain.QuoteStatus;
import com.converter.quote.dto.QuoteResponse;
import com.converter.quote.repository.QuoteRepository;
import com.converter.support.AbstractOrderPipelineIT;
import com.converter.supplier.dto.CreateSupplierRequest;
import com.converter.supplier.dto.PayAgainRequest;
import com.converter.supplier.dto.SupplierDetailResponse;
import com.converter.supplier.dto.UpdateSupplierRequest;
import com.converter.supplier.domain.Purpose;
import com.converter.treasury.domain.Currency;
import com.converter.treasury.dto.TreasuryAccountResponse;
import com.converter.user.domain.RoleCode;
import com.converter.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 4 — "payer a nouveau" : verifie que chaque appel produit un NOUVEAU Quote et un NOUVEL
 * Order, jamais une copie d'une transaction passee, en reutilisant integralement
 * {@code QuoteService}/{@code OrderService} (voir {@code RepeatPaymentService}).
 */
class RepeatPaymentServiceIT extends AbstractOrderPipelineIT {

    @Autowired
    private RepeatPaymentService repeatPaymentService;

    @Autowired
    private SupplierService supplierService;

    @Autowired
    private QuoteRepository quoteRepository;

    @Autowired
    private OrderTrackingService orderTrackingService;

    @Autowired
    private com.converter.order.service.OrderService orderService;

    private String setUpAdminWithLiquidity() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        return admin;
    }

    private SupplierDetailResponse createActiveSupplier(UUID ownerUserId, String bankName, String accountNumber) {
        CreateSupplierRequest request = new CreateSupplierRequest(BeneficiaryType.CHINESE_BANK_ACCOUNT,
                "Guangzhou Electronics Ltd.", "Guangzhou Electronics Ltd.", null, null, "China", "Guangzhou", null,
                bankName, "Tianhe Branch", "Zhang Wei", accountNumber, null, null, Currency.CNY,
                Purpose.IMPORT_GOODS, null);
        return supplierService.create(request, ownerUserId);
    }

    // ---- 1 / 2 / 3 / 7 : supplier actif -> nouveau Quote + nouvel Order + snapshot Beneficiary ----

    @Test
    void payAgain_withActiveSupplier_createsNewQuoteAndNewOrderWithBeneficiarySnapshot() {
        setUpAdminWithLiquidity();
        User userEntity = createUser(RoleCode.USER);
        SupplierDetailResponse supplier = createActiveSupplier(userEntity.getId(), "Bank of China", "6222021111111111");

        OrderDetailResponse order = repeatPaymentService.payAgain(supplier.id(),
                new PayAgainRequest(new BigDecimal("100000"), Purpose.IMPORT_GOODS, "Batch 1"), userEntity.getId());

        assertThat(order.id()).isNotNull();
        assertThat(order.quoteId()).isNotNull();
        assertThat(order.supplierId()).isEqualTo(supplier.id());
        assertThat(order.status().name()).isEqualTo("AWAITING_PAYMENT");
        assertThat(order.beneficiary().type()).isEqualTo(BeneficiaryType.CHINESE_BANK_ACCOUNT);
        assertThat(order.beneficiary().identifier()).isEqualTo("6222021111111111");
        assertThat(order.beneficiary().bankName()).isEqualTo("Bank of China");
    }

    // ---- 4 / 5 / 16 : ancien Quote/Order jamais reutilises, deux pay-again -> deux Orders distincts ----

    @Test
    void twoSuccessivePayAgainCalls_produceTwoIndependentOrdersAndQuotes() {
        setUpAdminWithLiquidity();
        User userEntity = createUser(RoleCode.USER);
        SupplierDetailResponse supplier = createActiveSupplier(userEntity.getId(), "Bank of China", "6222022222222222");

        OrderDetailResponse first = repeatPaymentService.payAgain(supplier.id(),
                new PayAgainRequest(new BigDecimal("100000"), null, null), userEntity.getId());
        OrderDetailResponse second = repeatPaymentService.payAgain(supplier.id(),
                new PayAgainRequest(new BigDecimal("120000"), null, null), userEntity.getId());

        assertThat(first.id()).isNotEqualTo(second.id());
        assertThat(first.quoteId()).isNotEqualTo(second.quoteId());
        assertThat(first.reference()).isNotEqualTo(second.reference());
        // Les deux ordres coexistent, aucun n'a ete efface/reutilise.
        assertThat(quoteRepository.findById(first.quoteId())).isPresent();
        assertThat(quoteRepository.findById(second.quoteId())).isPresent();
    }

    // ---- 22 (TEST CRITIQUE) : le pricing courant est utilise, jamais celui d'une transaction passee ----

    @Test
    void payAgain_reflectsCurrentPricing_neverTheRateOfAPreviousPayAgain() {
        String admin = setUpAdminWithLiquidity();
        User userEntity = createUser(RoleCode.USER);
        SupplierDetailResponse supplier = createActiveSupplier(userEntity.getId(), "Bank of China", "6222023333333333");

        OrderDetailResponse orderA = repeatPaymentService.payAgain(supplier.id(),
                new PayAgainRequest(new BigDecimal("100000"), null, null), userEntity.getId());
        BigDecimal rateA = orderA.customerRate();

        // Le pricing change reellement entre les deux appels (nouveau breakEvenRate).
        publishRate(admin, "95.000000");

        OrderDetailResponse orderB = repeatPaymentService.payAgain(supplier.id(),
                new PayAgainRequest(new BigDecimal("100000"), null, null), userEntity.getId());
        BigDecimal rateB = orderB.customerRate();

        assertThat(rateB).isNotEqualByComparingTo(rateA);
        assertThat(rateB).isGreaterThan(rateA);
        assertThat(orderA.quoteId()).isNotEqualTo(orderB.quoteId());
    }

    // ---- 23 (TEST CRITIQUE) / 8 : mutation du Supplier apres coup -> snapshot deja cree inchange ----

    @Test
    void payAgain_thenSupplierMutated_leavesTheAlreadyCreatedOrderSnapshotUnchanged() {
        setUpAdminWithLiquidity();
        User userEntity = createUser(RoleCode.USER);
        SupplierDetailResponse supplier = createActiveSupplier(userEntity.getId(), "Bank A", "1111");

        OrderDetailResponse order = repeatPaymentService.payAgain(supplier.id(),
                new PayAgainRequest(new BigDecimal("100000"), null, null), userEntity.getId());
        assertThat(order.beneficiary().bankName()).isEqualTo("Bank A");
        assertThat(order.beneficiary().identifier()).isEqualTo("1111");

        UpdateSupplierRequest update = new UpdateSupplierRequest(BeneficiaryType.CHINESE_BANK_ACCOUNT,
                "Guangzhou Electronics Ltd.", null, null, null, null, null, null, "Bank B", null, null, "2222",
                null, null, Currency.CNY, null, null);
        supplierService.update(supplier.id(), update, userEntity.getId());

        OrderDetailResponse reloaded = orderService.get(order.id(), userEntity.getId());
        assertThat(reloaded.beneficiary().bankName()).isEqualTo("Bank A");
        assertThat(reloaded.beneficiary().identifier()).isEqualTo("1111");
    }

    // ---- 9 : purpose explicitement fourni a priorite sur le motif par defaut du fournisseur ----

    @Test
    void payAgain_explicitPurpose_takesPriorityOverSupplierDefault() {
        setUpAdminWithLiquidity();
        User userEntity = createUser(RoleCode.USER);
        // Motif par defaut du fournisseur : IMPORT_GOODS (voir createActiveSupplier).
        SupplierDetailResponse supplier = createActiveSupplier(userEntity.getId(), "Bank of China", "6222024444444444");

        OrderDetailResponse order = repeatPaymentService.payAgain(supplier.id(),
                new PayAgainRequest(new BigDecimal("100000"), Purpose.SERVICES, "Consulting fee"), userEntity.getId());

        assertThat(order.purpose()).isEqualTo(Purpose.SERVICES);
        assertThat(order.purposeDetails()).isEqualTo("Consulting fee");
    }

    @Test
    void payAgain_purposeOmitted_fallsBackToSupplierDefault() {
        setUpAdminWithLiquidity();
        User userEntity = createUser(RoleCode.USER);
        SupplierDetailResponse supplier = createActiveSupplier(userEntity.getId(), "Bank of China", "6222025555555555");

        OrderDetailResponse order = repeatPaymentService.payAgain(supplier.id(),
                new PayAgainRequest(new BigDecimal("100000"), null, null), userEntity.getId());

        assertThat(order.purpose()).isEqualTo(Purpose.IMPORT_GOODS);
    }

    // ---- 10 : supplier desactive -> refus ----

    @Test
    void payAgain_deactivatedSupplier_isRejected() {
        setUpAdminWithLiquidity();
        User userEntity = createUser(RoleCode.USER);
        SupplierDetailResponse supplier = createActiveSupplier(userEntity.getId(), "Bank of China", "6222026666666666");
        supplierService.deactivate(supplier.id(), userEntity.getId());

        assertThatThrownBy(() -> repeatPaymentService.payAgain(supplier.id(),
                new PayAgainRequest(new BigDecimal("100000"), null, null), userEntity.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode().name()).isEqualTo("SUPPLIER_INACTIVE"));
    }

    // ---- 11 : supplier d'un autre utilisateur -> refus ----

    @Test
    void payAgain_anotherUsersSupplier_throwsSupplierNotFound() {
        setUpAdminWithLiquidity();
        User owner = createUser(RoleCode.USER);
        SupplierDetailResponse supplier = createActiveSupplier(owner.getId(), "Bank of China", "6222027777777777");
        UUID intruderId = createUser(RoleCode.USER).getId();

        assertThatThrownBy(() -> repeatPaymentService.payAgain(supplier.id(),
                new PayAgainRequest(new BigDecimal("100000"), null, null), intruderId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode().name()).isEqualTo("SUPPLIER_NOT_FOUND"));
    }

    // ---- 12 : supplier inexistant -> not found ----

    @Test
    void payAgain_unknownSupplier_throwsSupplierNotFound() {
        UUID userId = createUser(RoleCode.USER).getId();

        assertThatThrownBy(() -> repeatPaymentService.payAgain(UUID.randomUUID(),
                new PayAgainRequest(new BigDecimal("100000"), null, null), userId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode().name()).isEqualTo("SUPPLIER_NOT_FOUND"));
    }

    // ---- 13 / 14 / 15 : aucun Payment/Settlement automatique, treasury seulement reservee (comportement normal de creation) ----

    @Test
    void payAgain_neverCreatesPaymentOrSettlement_onlyReservesTreasuryLikeAnyNewOrder() {
        String admin = setUpAdminWithLiquidity();
        User userEntity = createUser(RoleCode.USER);
        SupplierDetailResponse supplier = createActiveSupplier(userEntity.getId(), "Bank of China", "6222028888888888");
        BigDecimal availableBefore = treasurySnapshot(admin, Currency.CNY).available();

        OrderDetailResponse order = repeatPaymentService.payAgain(supplier.id(),
                new PayAgainRequest(new BigDecimal("100000"), null, null), userEntity.getId());

        // Timeline reduite au seul ORDER_CREATED : aucun Payment/Settlement declenche.
        OrderTrackingResponse tracking = orderTrackingService.get(order.id(), userEntity.getId());
        assertThat(tracking.timeline()).extracting(TrackingEvent::code).containsExactly(
                com.converter.order.dto.TrackingEventCode.ORDER_CREATED);
        assertThat(tracking.currentStatus().name()).isEqualTo("AWAITING_PAYMENT");

        // La tresorerie est reservee (comportement deja existant de toute creation d'ordre,
        // TREASURY_RESERVE_ON_ORDER), jamais consommee/deposee en plus.
        BigDecimal availableAfter = treasurySnapshot(admin, Currency.CNY).available();
        assertThat(availableBefore.subtract(availableAfter)).isEqualByComparingTo(order.amountCny());
    }

    // ---- Quote intermediaire correctement accepte (precondition de OrderService.create) ----

    @Test
    void payAgain_theIntermediateQuoteEndsUpAccepted_neverLeftActiveOrOrphaned() {
        setUpAdminWithLiquidity();
        User userEntity = createUser(RoleCode.USER);
        SupplierDetailResponse supplier = createActiveSupplier(userEntity.getId(), "Bank of China", "6222029999999999");

        OrderDetailResponse order = repeatPaymentService.payAgain(supplier.id(),
                new PayAgainRequest(new BigDecimal("100000"), null, null), userEntity.getId());

        var quote = quoteRepository.findById(order.quoteId()).orElseThrow();
        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.ACCEPTED);
    }
}
