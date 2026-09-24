package com.converter.supplier.service;

import com.converter.common.exception.BusinessException;
import com.converter.order.domain.BeneficiaryType;
import com.converter.supplier.domain.Purpose;
import com.converter.supplier.domain.SupplierStatus;
import com.converter.supplier.dto.CreateSupplierRequest;
import com.converter.supplier.dto.SupplierDetailResponse;
import com.converter.supplier.dto.UpdateSupplierRequest;
import com.converter.support.AbstractRateQuoteIT;
import com.converter.treasury.domain.Currency;
import com.converter.user.domain.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifie {@link SupplierService} : CRUD, favori, desactivation, isolation par proprietaire. */
class SupplierServiceIT extends AbstractRateQuoteIT {

    @Autowired
    private SupplierService supplierService;

    private CreateSupplierRequest alipaySupplier(String displayName) {
        return new CreateSupplierRequest(BeneficiaryType.ALIPAY, displayName, "Shenzhen Textile Co", "+8613800000000",
                "supplier@example.com", "China", "Shenzhen", "Guangdong", null, null, "Li Wei",
                "alipay-id-123456", null, null, Currency.CNY, Purpose.IMPORT_GOODS, "Fournisseur textile principal");
    }

    private CreateSupplierRequest bankSupplier(String displayName, String bankName) {
        return new CreateSupplierRequest(BeneficiaryType.CHINESE_BANK_ACCOUNT, displayName, "Guangzhou Import Ltd",
                null, null, "China", "Guangzhou", "Guangdong", bankName, "Tianhe Branch", "Zhang Wei",
                "6222021234567890", "123 Tianhe Road, Guangzhou", "ICBKCNBJ", Currency.CNY, Purpose.BUSINESS, null);
    }

    @Test
    void create_persistsSupplierOwnedByCreator() {
        UUID userId = createUser(RoleCode.USER).getId();

        SupplierDetailResponse response = supplierService.create(alipaySupplier("Alipay Textile"), userId);

        assertThat(response.id()).isNotNull();
        assertThat(response.displayName()).isEqualTo("Alipay Textile");
        assertThat(response.accountNumber()).isEqualTo("alipay-id-123456");
        assertThat(response.status()).isEqualTo(SupplierStatus.ACTIVE);
        assertThat(response.favorite()).isFalse();
    }

    @Test
    void create_bankAccountWithoutBankName_isRejected() {
        UUID userId = createUser(RoleCode.USER).getId();

        assertThatThrownBy(() -> supplierService.create(bankSupplier("No Bank", null), userId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void get_anotherUsersSupplier_throwsSupplierNotFound() {
        UUID owner = createUser(RoleCode.USER).getId();
        UUID intruder = createUser(RoleCode.USER).getId();
        SupplierDetailResponse created = supplierService.create(alipaySupplier("Owner's supplier"), owner);

        assertThatThrownBy(() -> supplierService.get(created.id(), intruder))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode().name())
                        .isEqualTo("SUPPLIER_NOT_FOUND"));
    }

    @Test
    void update_anotherUsersSupplier_throwsSupplierNotFound() {
        UUID owner = createUser(RoleCode.USER).getId();
        UUID intruder = createUser(RoleCode.USER).getId();
        SupplierDetailResponse created = supplierService.create(alipaySupplier("Owner's supplier"), owner);
        UpdateSupplierRequest update = new UpdateSupplierRequest(BeneficiaryType.ALIPAY, "Hijacked", null, null,
                null, null, null, null, null, null, null, "new-account", null, null, Currency.CNY, null, null);

        assertThatThrownBy(() -> supplierService.update(created.id(), update, intruder))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void update_neverAffectsAlreadyPersistedValues_untilCalled() {
        UUID userId = createUser(RoleCode.USER).getId();
        SupplierDetailResponse created = supplierService.create(alipaySupplier("Original Name"), userId);

        UpdateSupplierRequest update = new UpdateSupplierRequest(BeneficiaryType.ALIPAY, "Renamed Supplier", null,
                null, null, null, null, null, null, null, null, "alipay-id-123456", null, null, Currency.CNY,
                Purpose.SERVICES, "Updated notes");
        SupplierDetailResponse updated = supplierService.update(created.id(), update, userId);

        assertThat(updated.displayName()).isEqualTo("Renamed Supplier");
        assertThat(updated.purpose()).isEqualTo(Purpose.SERVICES);
        assertThat(supplierService.get(created.id(), userId).displayName()).isEqualTo("Renamed Supplier");
    }

    @Test
    void favoriteAndUnfavorite_toggleTheFlag() {
        UUID userId = createUser(RoleCode.USER).getId();
        SupplierDetailResponse created = supplierService.create(alipaySupplier("Fav candidate"), userId);
        assertThat(created.favorite()).isFalse();

        SupplierDetailResponse favorited = supplierService.setFavorite(created.id(), userId, true);
        assertThat(favorited.favorite()).isTrue();

        SupplierDetailResponse unfavorited = supplierService.setFavorite(created.id(), userId, false);
        assertThat(unfavorited.favorite()).isFalse();
    }

    @Test
    void listFavorites_returnsOnlyFavoritedSuppliers() {
        UUID userId = createUser(RoleCode.USER).getId();
        SupplierDetailResponse fav = supplierService.create(alipaySupplier("Favorite One"), userId);
        supplierService.create(alipaySupplier("Not favorite"), userId);
        supplierService.setFavorite(fav.id(), userId, true);

        var page = supplierService.listFavorites(userId, PageRequest.of(0, 10));

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).displayName()).isEqualTo("Favorite One");
    }

    @Test
    void deactivate_setsStatusInactive_andNeverDeletesTheRow() {
        UUID userId = createUser(RoleCode.USER).getId();
        SupplierDetailResponse created = supplierService.create(alipaySupplier("To deactivate"), userId);

        SupplierDetailResponse deactivated = supplierService.deactivate(created.id(), userId);

        assertThat(deactivated.status()).isEqualTo(SupplierStatus.INACTIVE);
        // Toujours consultable : la desactivation est logique, jamais une suppression.
        assertThat(supplierService.get(created.id(), userId).status()).isEqualTo(SupplierStatus.INACTIVE);
    }

    @Test
    void list_filteredByStatus_excludesOtherStatuses() {
        UUID userId = createUser(RoleCode.USER).getId();
        SupplierDetailResponse active = supplierService.create(alipaySupplier("Active supplier"), userId);
        SupplierDetailResponse toDeactivate = supplierService.create(alipaySupplier("Will deactivate"), userId);
        supplierService.deactivate(toDeactivate.id(), userId);

        var activeOnly = supplierService.list(userId, SupplierStatus.ACTIVE, PageRequest.of(0, 10));

        assertThat(activeOnly.content()).extracting("id").containsExactly(active.id());
    }

    @Test
    void listSummary_masksAccountNumber() {
        UUID userId = createUser(RoleCode.USER).getId();
        supplierService.create(alipaySupplier("Masked account"), userId);

        var page = supplierService.list(userId, null, PageRequest.of(0, 10));

        assertThat(page.content().get(0).maskedAccountNumber()).isEqualTo("******3456");
    }

    @Test
    void anotherUsersSuppliers_areNeverVisibleInMyList() {
        UUID userA = createUser(RoleCode.USER).getId();
        UUID userB = createUser(RoleCode.USER).getId();
        supplierService.create(alipaySupplier("User A's supplier"), userA);
        supplierService.create(alipaySupplier("User B's supplier"), userB);

        var pageA = supplierService.list(userA, null, PageRequest.of(0, 10));

        assertThat(pageA.content()).hasSize(1);
        assertThat(pageA.content().get(0).displayName()).isEqualTo("User A's supplier");
    }
}
