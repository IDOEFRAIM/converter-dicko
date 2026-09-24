package com.converter.rate.cost.service;

import com.converter.audit.domain.AuditAction;
import com.converter.audit.service.AuditService;
import com.converter.common.exception.ErrorCode;
import com.converter.common.exception.ResourceNotFoundException;
import com.converter.rate.cost.BreakEvenResult;
import com.converter.rate.cost.CostRateCalculator;
import com.converter.rate.cost.domain.DailyCostRateConfiguration;
import com.converter.rate.cost.dto.CostRateConfigurationResponse;
import com.converter.rate.cost.dto.PublishCostRateConfigurationRequest;
import com.converter.rate.cost.repository.DailyCostRateConfigurationRepository;
import com.converter.rate.engine.RateEngine;
import com.converter.rate.provider.RateProvider;
import com.converter.rate.publicrate.service.PublicRateSnapshotService;
import com.converter.settings.domain.SettingKey;
import com.converter.settings.service.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Permet a un administrateur de publier les parametres de cout du jour
 * (chaine XOF -&gt; USD -&gt; CNY) et d'en historiser le
 * {@code breakEvenRate} calcule.
 *
 * <p>Ce service ne touche jamais {@code RateSource} ni un {@code Quote}
 * deja cree — mais depuis la Phase 3.1, la ligne qu'il publie ici EST
 * directement consommee par la creation de tout nouveau {@code Quote}
 * (via {@link com.converter.rate.cost.provider.CostRateProvider}), pas
 * seulement affichee a titre indicatif pour un administrateur. Publier
 * une configuration erronee affecte donc immediatement le
 * {@code customerRate} propose aux clients suivants.
 */
@Service
public class CostRateAdminService {

    private static final Logger log = LoggerFactory.getLogger(CostRateAdminService.class);

    private final DailyCostRateConfigurationRepository repository;
    private final CostRateCalculator calculator;
    private final PublicRateSnapshotService publicRateSnapshotService;
    private final AuditService auditService;
    private final Clock clock;
    private final RateEngine rateEngine;
    private final SettingsService settingsService;

    public CostRateAdminService(DailyCostRateConfigurationRepository repository,
                                CostRateCalculator calculator,
                                PublicRateSnapshotService publicRateSnapshotService,
                                AuditService auditService,
                                Clock clock,
                                RateEngine rateEngine,
                                SettingsService settingsService) {
        this.repository = repository;
        this.calculator = calculator;
        this.publicRateSnapshotService = publicRateSnapshotService;
        this.auditService = auditService;
        this.clock = clock;
        this.rateEngine = rateEngine;
        this.settingsService = settingsService;
    }

    @Transactional
    public CostRateConfigurationResponse publish(PublishCostRateConfigurationRequest request, UUID actorId) {
        BreakEvenResult result = calculator.calculateBreakEven(
                request.referenceAmountXof(),
                request.rateXofUsd(),
                request.rateUsdCny(),
                request.feeXofUsdPercent(),
                request.feeUsdCnyFixedUsd());

        Instant now = clock.instant();
        DailyCostRateConfiguration configuration = new DailyCostRateConfiguration(
                request.businessDate(),
                result,
                request.rateXofUsd(),
                request.rateUsdCny(),
                request.feeXofUsdPercent(),
                request.feeUsdCnyFixedUsd(),
                request.note(),
                actorId,
                now);
        DailyCostRateConfiguration saved = repository.save(configuration);

        // Additif uniquement : ne touche ni la validation, ni le calcul du breakEven, ni la
        // persistance de la configuration, ni l'audit ci-dessous, ni la transaction existante —
        // rejoint la meme transaction (propagation REQUIRED par defaut), donc atomique avec la
        // ligne ci-dessus (voir PublicRateSnapshotService : frontiere de confidentialite, ne
        // recoit jamais la configuration elle-meme, uniquement le breakEvenRate deja calcule).
        publicRateSnapshotService.record(result.breakEvenRate(), RateProvider.DEFAULT_CURRENCY_PAIR, now);

        auditService.record(actorId, null, AuditAction.COST_RATE_CONFIGURATION_PUBLISHED,
                "DailyCostRateConfiguration", saved.getId().toString(),
                "{\"businessDate\":\"" + request.businessDate() + "\",\"breakEvenRate\":\""
                        + result.breakEvenRate() + "\"}");
        log.info("Configuration de cout XOF->USD->CNY publiee pour {} par {} : breakEvenRate={}",
                request.businessDate(), actorId, result.breakEvenRate());

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public CostRateConfigurationResponse current() {
        return repository.findFirstByOrderByCreatedAtDesc()
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Aucune configuration de cout n'a encore ete publiee."));
    }

    @Transactional(readOnly = true)
    public Page<CostRateConfigurationResponse> history(Pageable pageable) {
        return repository.findAllByOrderByCreatedAtDesc(pageable).map(this::toResponse);
    }

    /**
     * Recalcule {@code marginPercentage}/{@code customerRate} avec la marge
     * <b>actuellement active</b> (jamais persistes sur la configuration
     * elle-meme) — meme formule que {@link PublicRateSnapshotService#record},
     * pour que l'administrateur voie exactement le nombre propose au client
     * a l'instant present, y compris pour une ligne d'historique ancienne.
     */
    private CostRateConfigurationResponse toResponse(DailyCostRateConfiguration configuration) {
        BigDecimal marginPercentage = settingsService.getDecimal(SettingKey.DEFAULT_MARGIN_PERCENTAGE);
        BigDecimal customerRate = rateEngine.applyMargin(configuration.getBreakEvenRate(), marginPercentage);
        return new CostRateConfigurationResponse(
                configuration.getId(),
                configuration.getBusinessDate(),
                configuration.getRateXofUsd(),
                configuration.getRateUsdCny(),
                configuration.getFeeXofUsdPercent(),
                configuration.getFeeUsdCnyFixedUsd(),
                configuration.getReferenceAmountXof(),
                configuration.getBreakEvenRate(),
                marginPercentage,
                customerRate,
                configuration.getNote(),
                configuration.getCreatedAt());
    }
}
