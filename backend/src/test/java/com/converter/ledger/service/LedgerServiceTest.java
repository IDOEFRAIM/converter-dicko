package com.converter.ledger.service;

import com.converter.audit.domain.AuditAction;
import com.converter.audit.service.AuditService;
import com.converter.ledger.domain.LedgerEntry;
import com.converter.ledger.domain.LedgerEntryType;
import com.converter.ledger.repository.LedgerEntryRepository;
import com.converter.order.domain.Order;
import com.converter.treasury.domain.Currency;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link LedgerService} : le point d'entree commun (flux manuel aujourd'hui,
 * TransFi demain) qui enregistre le revenu de frais de service — voir
 * {@code docs/TRANSFI_INTEGRATION.md}.
 */
@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock
    private LedgerEntryRepository repository;

    @Mock
    private AuditService auditService;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-25T12:00:00Z"), ZoneOffset.UTC);

    private LedgerService ledgerService;

    @BeforeEach
    void setUp() {
        ledgerService = new LedgerService(repository, auditService, clock);
    }

    @Test
    void recordServiceFeeForOrder_withPositiveFee_recordsOnce() {
        Order order = orderWithFee(new BigDecimal("1500"));
        when(repository.existsByOrderIdAndEntryType(order.getId(), LedgerEntryType.SERVICE_FEE)).thenReturn(false);
        when(repository.saveAndFlush(any(LedgerEntry.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        ledgerService.recordServiceFeeForOrder(order);

        verify(repository, times(1)).saveAndFlush(any(LedgerEntry.class));
        verify(auditService).recordSystem(eq(AuditAction.LEDGER_ENTRY_RECORDED), any(), any(), any());
    }

    @Test
    void recordServiceFeeForOrder_withNoFee_isNoOp() {
        Order order = orderWithFee(null);

        ledgerService.recordServiceFeeForOrder(order);

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void recordServiceFeeForOrder_withZeroFee_isNoOp() {
        Order order = orderWithFee(BigDecimal.ZERO);

        ledgerService.recordServiceFeeForOrder(order);

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void recordServiceFeeForOrder_whenAlreadyRecorded_isNoOp() {
        Order order = orderWithFee(new BigDecimal("1500"));
        when(repository.existsByOrderIdAndEntryType(order.getId(), LedgerEntryType.SERVICE_FEE)).thenReturn(true);

        ledgerService.recordServiceFeeForOrder(order);

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void recordServiceFeeForOrder_onConcurrentDuplicate_swallowsConstraintViolation() {
        Order order = orderWithFee(new BigDecimal("1500"));
        when(repository.existsByOrderIdAndEntryType(order.getId(), LedgerEntryType.SERVICE_FEE)).thenReturn(false);
        when(repository.saveAndFlush(any(LedgerEntry.class)))
                .thenThrow(new DataIntegrityViolationException("uq_ledger_entries_order_service_fee"));

        // Ne doit jamais propager l'exception : la transition d'ordre (transitionToCompleted) ne
        // doit jamais echouer a cause d'une course sur le ledger.
        ledgerService.recordServiceFeeForOrder(order);
    }

    @Test
    void record_persistsEntryAndAudits() {
        UUID orderId = UUID.randomUUID();
        when(repository.saveAndFlush(any(LedgerEntry.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        LedgerEntry saved = ledgerService.record(orderId, LedgerEntryType.PROVIDER_FEE, Currency.CNY,
                new BigDecimal("50"), "Frais TransFi");

        assertThat(saved.getOrderId()).isEqualTo(orderId);
        assertThat(saved.getEntryType()).isEqualTo(LedgerEntryType.PROVIDER_FEE);
        assertThat(saved.getAmount()).isEqualByComparingTo("50");
        verify(auditService).recordSystem(eq(AuditAction.LEDGER_ENTRY_RECORDED), any(), any(), any());
    }

    private static LedgerEntry withId(LedgerEntry entry) {
        entry.setId(UUID.randomUUID());
        return entry;
    }

    private static Order orderWithFee(BigDecimal feeXof) {
        Order order = new Order("REF-1", UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100000"),
                new BigDecimal("1000"), new BigDecimal("85"), feeXof, new BigDecimal("98500"), null,
                Instant.parse("2026-09-25T10:00:00Z"), Instant.parse("2026-09-26T10:00:00Z"));
        order.setId(UUID.randomUUID());
        return order;
    }
}
