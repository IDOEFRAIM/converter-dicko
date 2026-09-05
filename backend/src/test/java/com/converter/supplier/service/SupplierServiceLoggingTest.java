package com.converter.supplier.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.converter.audit.service.AuditService;
import com.converter.order.domain.BeneficiaryType;
import com.converter.security.OwnershipService;
import com.converter.supplier.domain.Purpose;
import com.converter.supplier.domain.Supplier;
import com.converter.supplier.dto.CreateSupplierRequest;
import com.converter.supplier.dto.UpdateSupplierRequest;
import com.converter.supplier.repository.SupplierRepository;
import com.converter.treasury.domain.Currency;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Section 18 — protection des donnees : aucune ligne de log emise par {@link SupplierService} ne
 * doit jamais porter un {@code accountNumber} complet, ni a la creation ni a la mise a jour.
 * Repository et audit entierement simules : seul le comportement de journalisation est en jeu.
 */
@ExtendWith(MockitoExtension.class)
class SupplierServiceLoggingTest {

    private static final String FULL_ACCOUNT_NUMBER = "6222021234567890";

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private AuditService auditService;

    private SupplierService service;
    private ListAppender<ILoggingEvent> appender;
    private Logger logbackLogger;

    @BeforeEach
    void setUp() {
        service = new SupplierService(supplierRepository, new OwnershipService(), auditService);

        logbackLogger = (Logger) LoggerFactory.getLogger(SupplierService.class);
        appender = new ListAppender<>();
        appender.start();
        logbackLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logbackLogger.detachAppender(appender);
    }

    @Test
    void create_neverLogsTheFullAccountNumber() {
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(invocation -> {
            Supplier supplier = invocation.getArgument(0);
            supplier.setId(UUID.randomUUID());
            return supplier;
        });
        CreateSupplierRequest request = new CreateSupplierRequest(BeneficiaryType.CHINESE_BANK_ACCOUNT,
                "Bank supplier", null, null, null, null, null, null, "Bank of China", null, "Zhang Wei",
                FULL_ACCOUNT_NUMBER, null, null, Currency.CNY, Purpose.IMPORT_GOODS, null);

        service.create(request, UUID.randomUUID());

        assertNoLogContainsAccountNumber();
    }

    @Test
    void update_neverLogsTheFullAccountNumber() {
        UUID ownerId = UUID.randomUUID();
        Supplier existing = new Supplier(ownerId, BeneficiaryType.CHINESE_BANK_ACCOUNT, "Bank supplier", null, null,
                null, null, null, null, "Bank of China", null, "Zhang Wei", "old-account-number", null, null,
                Currency.CNY, null, null);
        existing.setId(UUID.randomUUID());
        when(supplierRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

        UpdateSupplierRequest update = new UpdateSupplierRequest(BeneficiaryType.CHINESE_BANK_ACCOUNT,
                "Bank supplier", null, null, null, null, null, null, "Bank of China", null, "Zhang Wei",
                FULL_ACCOUNT_NUMBER, null, null, Currency.CNY, null, null);

        service.update(existing.getId(), update, ownerId);

        assertNoLogContainsAccountNumber();
    }

    private void assertNoLogContainsAccountNumber() {
        assertThat(appender.list)
                .as("aucune ligne de log ne doit contenir le numero de compte complet")
                .noneMatch(event -> event.getFormattedMessage().contains(FULL_ACCOUNT_NUMBER));
    }
}
