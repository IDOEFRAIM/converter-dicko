package com.converter.order.service;

import com.converter.common.api.ApiResponse;
import com.converter.common.exception.BusinessException;
import com.converter.order.domain.BeneficiaryType;
import com.converter.order.domain.Order;
import com.converter.order.dto.CreateOrderRequest;
import com.converter.order.dto.OrderDetailResponse;
import com.converter.order.repository.OrderRepository;
import com.converter.quote.dto.QuoteResponse;
import com.converter.support.AbstractOrderPipelineIT;
import com.converter.supplier.domain.Purpose;
import com.converter.supplier.dto.CreateSupplierRequest;
import com.converter.supplier.dto.SupplierDetailResponse;
import com.converter.supplier.dto.UpdateSupplierRequest;
import com.converter.supplier.service.SupplierService;
import com.converter.treasury.domain.Currency;
import com.converter.user.domain.RoleCode;
import com.converter.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.ParameterizedTypeReference;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 2 — lien tracable Order.supplierId + purpose/purposeDetails. Verifie explicitement que
 * le fournisseur enregistre n'est jamais qu'une source de donnees a la creation : le snapshot
 * {@code Beneficiary} qui en resulte reste, comme toujours, entierement fige.
 */
class OrderSupplierIT extends AbstractOrderPipelineIT {

    @Autowired
    private SupplierService supplierService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderService orderService;

    private SupplierDetailResponse createActiveSupplier(UUID ownerUserId, String displayName, String accountNumber) {
        CreateSupplierRequest request = new CreateSupplierRequest(BeneficiaryType.ALIPAY, displayName,
                "Legal " + displayName, null, null, "China", "Shenzhen", null, null, null, null, accountNumber,
                null, null, Currency.CNY, Purpose.IMPORT_GOODS, null);
        return supplierService.create(request, ownerUserId);
    }

    // ---- 1 / 9 : comportement historique inchange, sans supplier ni purpose ----

    @Test
    void createOrder_withoutSupplier_behavesExactlyAsBeforeAndLeavesNewFieldsNull() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "100000");

        OrderDetailResponse order = createOrder(user, quote.id(), alipayBeneficiary());

        assertThat(order.id()).isNotNull();
        assertThat(order.beneficiary().identifier()).isEqualTo("zhang.san@example.com");
        assertThat(order.supplierId()).isNull();
        assertThat(order.purpose()).isNull();
        assertThat(order.purposeDetails()).isNull();
    }

    // ---- 2 / 3 : creation via un fournisseur actif, copie dans le snapshot ----

    @Test
    void createOrder_withActiveSupplier_copiesSupplierDataIntoBeneficiarySnapshot() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        User userEntity = createUser(RoleCode.USER);
        UUID userId = userEntity.getId();
        String user = tokenFor(userEntity);
        SupplierDetailResponse supplier = createActiveSupplier(userId, "My Textile Supplier", "alipay-acc-001");
        QuoteResponse quote = createAcceptedQuote(user, "100000");

        ResponseEntity<ApiResponse<OrderDetailResponse>> response =
                createOrderWithSupplierRaw(user, quote.id(), supplier.id());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        OrderDetailResponse order = response.getBody().data();
        assertThat(order.supplierId()).isEqualTo(supplier.id());
        assertThat(order.beneficiary().type()).isEqualTo(BeneficiaryType.ALIPAY);
        assertThat(order.beneficiary().identifier()).isEqualTo("alipay-acc-001");
        assertThat(order.beneficiary().fullName()).isEqualTo("Legal My Textile Supplier");
    }

    // ---- 4 : une modification ulterieure du Supplier n'affecte pas l'ordre deja cree ----

    @Test
    void supplierModifiedAfterOrderCreation_neverAltersTheAlreadyCreatedOrder() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        User userEntity = createUser(RoleCode.USER);
        UUID userId = userEntity.getId();
        String user = tokenFor(userEntity);
        SupplierDetailResponse supplier = createActiveSupplier(userId, "Original Name", "original-account");
        QuoteResponse quote = createAcceptedQuote(user, "100000");
        OrderDetailResponse order = createOrderWithSupplierRaw(user, quote.id(), supplier.id()).getBody().data();

        UpdateSupplierRequest update = new UpdateSupplierRequest(BeneficiaryType.ALIPAY, "Renamed Later", null,
                null, null, null, null, null, null, null, null, "changed-account-number", null, null, Currency.CNY,
                null, null);
        supplierService.update(supplier.id(), update, userId);

        ResponseEntity<ApiResponse<OrderDetailResponse>> reloaded = restTemplate.exchange(
                "/api/v1/orders/" + order.id(), HttpMethod.GET, new HttpEntity<>(auth(user)),
                new ParameterizedTypeReference<ApiResponse<OrderDetailResponse>>() {
                });

        assertThat(reloaded.getBody().data().beneficiary().identifier()).isEqualTo("original-account");
        assertThat(reloaded.getBody().data().beneficiary().fullName()).isEqualTo("Legal Original Name");
    }

    // ---- 5 : fournisseur desactive -> nouvel ordre refuse, historique toujours consultable ----

    @Test
    void deactivatedSupplier_cannotBeUsedForANewOrder_butExistingOrdersRemainReadable() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "2000000");
        User userEntity = createUser(RoleCode.USER);
        UUID userId = userEntity.getId();
        String user = tokenFor(userEntity);
        SupplierDetailResponse supplier = createActiveSupplier(userId, "Will be deactivated", "acc-deact");
        QuoteResponse firstQuote = createAcceptedQuote(user, "50000");
        OrderDetailResponse existingOrder =
                createOrderWithSupplierRaw(user, firstQuote.id(), supplier.id()).getBody().data();

        supplierService.deactivate(supplier.id(), userId);

        QuoteResponse secondQuote = createAcceptedQuote(user, "50000");
        ResponseEntity<ApiResponse<OrderDetailResponse>> rejected =
                createOrderWithSupplierRaw(user, secondQuote.id(), supplier.id());

        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<ApiResponse<OrderDetailResponse>> stillReadable = restTemplate.exchange(
                "/api/v1/orders/" + existingOrder.id(), HttpMethod.GET, new HttpEntity<>(auth(user)),
                new ParameterizedTypeReference<ApiResponse<OrderDetailResponse>>() {
                });
        assertThat(stillReadable.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stillReadable.getBody().data().supplierId()).isEqualTo(supplier.id());
    }

    // ---- 6 : fournisseur d'un autre utilisateur -> refuse, niveau service ET HTTP ----

    @Test
    void createOrder_withAnotherUsersSupplier_throwsSupplierNotFound_atServiceLevel() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        UUID ownerId = createUser(RoleCode.USER).getId();
        SupplierDetailResponse supplier = createActiveSupplier(ownerId, "Owner's supplier", "owner-acc");
        UUID intruderId = createUser(RoleCode.USER).getId();
        String intruder = tokenFor(userRepository.findById(intruderId).orElseThrow());
        QuoteResponse quote = createAcceptedQuote(intruder, "50000");

        CreateOrderRequest request = new CreateOrderRequest(quote.id(), null, "test", supplier.id(), null, null, null);

        assertThatThrownBy(() -> orderService.create(request, intruderId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode().name())
                        .isEqualTo("SUPPLIER_NOT_FOUND"));
    }

    @Test
    void createOrder_withAnotherUsersSupplier_returns404_atHttpLevel() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        UUID ownerId = createUser(RoleCode.USER).getId();
        SupplierDetailResponse supplier = createActiveSupplier(ownerId, "Owner's supplier", "owner-acc-2");
        String intruder = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(intruder, "50000");

        ResponseEntity<ApiResponse<OrderDetailResponse>> response =
                createOrderWithSupplierRaw(intruder, quote.id(), supplier.id());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---- 7 / 8 : purpose et purposeDetails persistes ----

    @Test
    void createOrder_withPurposeAndDetails_persistsBoth() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "100000");

        ResponseEntity<ApiResponse<OrderDetailResponse>> response = restTemplate.exchange(
                "/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>(new CreateOrderRequest(quote.id(), alipayBeneficiary(), "test", null,
                        Purpose.IMPORT_GOODS, "Bulk textile order, batch 12", null), auth(user)),
                new ParameterizedTypeReference<ApiResponse<OrderDetailResponse>>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        OrderDetailResponse order = response.getBody().data();
        assertThat(order.purpose()).isEqualTo(Purpose.IMPORT_GOODS);
        assertThat(order.purposeDetails()).isEqualTo("Bulk textile order, batch 12");

        ResponseEntity<ApiResponse<OrderDetailResponse>> reloaded = restTemplate.exchange(
                "/api/v1/orders/" + order.id(), HttpMethod.GET, new HttpEntity<>(auth(user)),
                new ParameterizedTypeReference<ApiResponse<OrderDetailResponse>>() {
                });
        assertThat(reloaded.getBody().data().purpose()).isEqualTo(Purpose.IMPORT_GOODS);
        assertThat(reloaded.getBody().data().purposeDetails()).isEqualTo("Bulk textile order, batch 12");
    }

    // ---- validation : exactement l'un de beneficiary/supplierId ----

    @Test
    void createOrder_withBothBeneficiaryAndSupplierId_returnsValidationError() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        User userEntity = createUser(RoleCode.USER);
        UUID userId = userEntity.getId();
        String user = tokenFor(userEntity);
        SupplierDetailResponse supplier = createActiveSupplier(userId, "Ambiguous input supplier", "acc-ambig");
        QuoteResponse quote = createAcceptedQuote(user, "50000");

        ResponseEntity<ApiResponse<OrderDetailResponse>> response = restTemplate.exchange(
                "/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>(new CreateOrderRequest(quote.id(), alipayBeneficiary(), "test", supplier.id(),
                        null, null, null), auth(user)),
                new ParameterizedTypeReference<ApiResponse<OrderDetailResponse>>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createOrder_withNeitherBeneficiaryNorSupplierId_returnsValidationError() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        String user = tokenFor(createUser(RoleCode.USER));
        QuoteResponse quote = createAcceptedQuote(user, "50000");

        ResponseEntity<ApiResponse<OrderDetailResponse>> response = restTemplate.exchange(
                "/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>(new CreateOrderRequest(quote.id(), null, "test", null, null, null, null), auth(user)),
                new ParameterizedTypeReference<ApiResponse<OrderDetailResponse>>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ---- 10 : la contrainte FK PostgreSQL est reellement appliquee, pas seulement verifiee cote appli ----

    @Test
    void supplierIdWithNoMatchingSupplierRow_violatesForeignKeyConstraintAtTheDatabaseLevel() {
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        User userEntity = createUser(RoleCode.USER);
        UUID userId = userEntity.getId();
        String user = tokenFor(userEntity);
        QuoteResponse quote = createAcceptedQuote(user, "50000");

        // Contourne volontairement OrderService (qui rejetterait deja cote application) pour
        // prouver que la contrainte fk_orders_supplier existe reellement en base, independamment
        // du code applicatif — meme discipline que les contraintes deja verifiees ailleurs dans
        // le projet (ex. uq_orders_quote).
        Order order = new Order("FK-TEST-" + UUID.randomUUID().toString().substring(0, 8), userId, quote.id(),
                new BigDecimal("50000.00"), new BigDecimal("588.23"), new BigDecimal("85.000000"),
                BigDecimal.ZERO, new BigDecimal("50000.00"), null, Instant.now(), Instant.now().plusSeconds(3600),
                UUID.randomUUID(), null, null);

        assertThatThrownBy(() -> orderRepository.saveAndFlush(order))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
