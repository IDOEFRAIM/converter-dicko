package com.converter.rate.publicrate.service;

import com.converter.common.api.PageResponse;
import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.rate.engine.RateEngine;
import com.converter.rate.publicrate.domain.PublicRateSnapshot;
import com.converter.rate.publicrate.dto.PublicRateHistoryEntry;
import com.converter.rate.publicrate.repository.PublicRateSnapshotRepository;
import com.converter.settings.domain.SettingKey;
import com.converter.settings.service.SettingsService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * Frontiere de confidentialite entre le pricing interne et l'API publique.
 *
 * <pre>
 * RAW / INTERNAL COST
 *         v
 *    BREAK-EVEN            (confidentiel — daily_cost_rate_configurations)
 *         v
 * COMMERCIAL PRICING        (RateEngine.applyMargin — SEULE formule de calcul, jamais dupliquee)
 *         v
 * PUBLIC CUSTOMER RATE      (public_rate_snapshots — uniquement ce que ce service persiste)
 * </pre>
 *
 * <p>{@link #record} est appele par {@code CostRateAdminService.publish()} — jamais l'inverse —
 * avec le seul {@code breakEvenRate} venant d'etre calcule, jamais la configuration de cout
 * elle-meme : ce service n'a et ne doit jamais avoir de dependance vers
 * {@code DailyCostRateConfiguration}/{@code DailyCostRateConfigurationRepository}, seule garantie
 * structurelle qu'aucune donnee interne ne puisse un jour fuiter par ce chemin.
 */
@Service
public class PublicRateSnapshotService {

    /** Seules paires reellement supportees aujourd'hui — voir RateProvider.DEFAULT_CURRENCY_PAIR. */
    private static final Set<String> SUPPORTED_PAIRS = Set.of("XOF/CNY");

    private final PublicRateSnapshotRepository repository;
    private final RateEngine rateEngine;
    private final SettingsService settingsService;

    public PublicRateSnapshotService(PublicRateSnapshotRepository repository, RateEngine rateEngine,
                                     SettingsService settingsService) {
        this.repository = repository;
        this.rateEngine = rateEngine;
        this.settingsService = settingsService;
    }

    /**
     * Calcule le {@code customerRate} avec la marge <b>actuellement active</b> (comme tout devis
     * cree a cet instant) et le persiste, append-only. Ne recoit et ne lit jamais la
     * configuration de cout elle-meme — uniquement le {@code breakEvenRate} deja calcule par
     * l'appelant, qui reste l'unique proprietaire de cette donnee confidentielle.
     */
    @Transactional
    public void record(BigDecimal breakEvenRate, String currencyPair, Instant recordedAt) {
        BigDecimal marginPercentage = settingsService.getDecimal(SettingKey.DEFAULT_MARGIN_PERCENTAGE);
        BigDecimal customerRate = rateEngine.applyMargin(breakEvenRate, marginPercentage);
        repository.save(new PublicRateSnapshot(currencyPair, customerRate, recordedAt));
    }

    @Transactional(readOnly = true)
    public PageResponse<PublicRateHistoryEntry> history(String currencyPair, Instant from, Instant to,
                                                         Pageable pageable) {
        if (!SUPPORTED_PAIRS.contains(currencyPair)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Paire de devises non supportee : " + currencyPair + ".");
        }
        // Le tri (recordedAt DESC, id DESC) est fixe dans la requete du repository, non
        // negociable par l'appelant : seuls le numero et la taille de page sont retenus d'un
        // Pageable fourni par le controleur, tout tri qu'il porterait est ignore.
        Pageable pageOnly = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        Page<PublicRateSnapshot> page = repository.search(currencyPair, from != null, from, to != null, to, pageOnly);
        return PageResponse.from(page, PublicRateSnapshotService::toEntry);
    }

    /**
     * Dernier {@code customerRate} publie pour {@code currencyPair}, ou vide si aucun snapshot
     * n'existe encore. Destine a {@code RateAlertService} (Phase 6) : une alerte compare toujours
     * l'objectif utilisateur a ce meme taux client public, jamais a un taux interne — voir la
     * Javadoc de classe.
     */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> latestCustomerRate(String currencyPair) {
        return repository.findFirstByCurrencyPairOrderByRecordedAtDescIdDesc(currencyPair)
                .map(PublicRateSnapshot::getCustomerRate);
    }

    private static PublicRateHistoryEntry toEntry(PublicRateSnapshot snapshot) {
        return new PublicRateHistoryEntry(snapshot.getCurrencyPair(), snapshot.getCustomerRate(),
                snapshot.getRecordedAt());
    }
}
