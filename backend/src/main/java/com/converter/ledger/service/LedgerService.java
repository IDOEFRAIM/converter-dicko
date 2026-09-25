package com.converter.ledger.service;

import com.converter.audit.domain.AuditAction;
import com.converter.audit.service.AuditService;
import com.converter.common.api.PageResponse;
import com.converter.common.util.JsonUtil;
import com.converter.ledger.domain.LedgerEntry;
import com.converter.ledger.domain.LedgerEntryType;
import com.converter.ledger.dto.LedgerEntryResponse;
import com.converter.ledger.repository.LedgerEntryRepository;
import com.converter.order.domain.Order;
import com.converter.treasury.domain.Currency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Grand livre interne : trace le revenu reel (frais de service deja factures) et, une fois
 * TransFi branche, les couts prestataire reels — voir {@code docs/TRANSFI_INTEGRATION.md}. Ne
 * calcule jamais de tarification (c'est le role de {@code RateEngine}) : il enregistre des
 * montants deja connus.
 */
@Service
public class LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

    private final LedgerEntryRepository repository;
    private final AuditService auditService;
    private final Clock clock;

    public LedgerService(LedgerEntryRepository repository, AuditService auditService, Clock clock) {
        this.repository = repository;
        this.auditService = auditService;
        this.clock = clock;
    }

    /**
     * Enregistre notre revenu de frais de service pour un ordre qui vient d'etre marque
     * {@code COMPLETED} — appele depuis {@code OrderService#transitionToCompleted}, donc a
     * l'identique que le reglement ait ete execute manuellement (aujourd'hui) ou automatiquement
     * via TransFi (demain) : un seul point d'entree, jamais duplique entre les deux chemins.
     *
     * <p>Idempotent : un ordre ne peut generer qu'une seule ligne {@code SERVICE_FEE} (contrainte
     * {@code uq_ledger_entries_order_service_fee}, V44) — un second appel pour le meme ordre est
     * un no-op silencieux, jamais une erreur qui bloquerait la transition de l'ordre.
     */
    @Transactional
    public void recordServiceFeeForOrder(Order order) {
        if (order.getFeeXof() == null || order.getFeeXof().signum() <= 0) {
            return;
        }
        if (repository.existsByOrderIdAndEntryType(order.getId(), LedgerEntryType.SERVICE_FEE)) {
            return;
        }
        try {
            record(order.getId(), LedgerEntryType.SERVICE_FEE, Currency.XOF, order.getFeeXof(),
                    "Frais de service, ordre #" + order.getReference());
        } catch (DataIntegrityViolationException ex) {
            // Course entre deux appels concurrents (improbable : transitionToCompleted prend deja
            // un verrou pessimiste sur l'ordre) -- la contrainte unique a deja fait son travail,
            // rien de plus a faire ici (meme discipline defensive que SettlementService.create).
            log.debug("Ligne SERVICE_FEE deja presente pour l'ordre {} (course concurrente).", order.getId());
        }
    }

    /** Point d'entree generique — utilise pour les frais prestataire (webhook TransFi) et les corrections manuelles. */
    @Transactional
    public LedgerEntry record(UUID orderId, LedgerEntryType type, Currency currency, BigDecimal amount,
                              String description) {
        Instant now = clock.instant();
        LedgerEntry saved = repository.saveAndFlush(new LedgerEntry(orderId, type, currency, amount, description, now));
        auditService.recordSystem(AuditAction.LEDGER_ENTRY_RECORDED, "LedgerEntry", saved.getId().toString(),
                "{\"orderId\":" + (orderId == null ? "null" : JsonUtil.jsonString(orderId.toString()))
                        + ",\"type\":" + JsonUtil.jsonString(type.name())
                        + ",\"amount\":" + JsonUtil.jsonString(amount.toPlainString()) + "}");
        return saved;
    }

    @Transactional(readOnly = true)
    public PageResponse<LedgerEntryResponse> list(Pageable pageable) {
        Page<LedgerEntry> page = repository.findAllByOrderByCreatedAtDesc(pageable);
        return PageResponse.from(page, LedgerService::toResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<LedgerEntryResponse> listForOrder(UUID orderId, Pageable pageable) {
        Page<LedgerEntry> page = repository.findAllByOrderIdOrderByCreatedAtDesc(orderId, pageable);
        return PageResponse.from(page, LedgerService::toResponse);
    }

    private static LedgerEntryResponse toResponse(LedgerEntry entry) {
        return new LedgerEntryResponse(entry.getId(), entry.getOrderId(), entry.getEntryType(), entry.getCurrency(),
                entry.getAmount(), entry.getDescription(), entry.getCreatedAt());
    }
}
