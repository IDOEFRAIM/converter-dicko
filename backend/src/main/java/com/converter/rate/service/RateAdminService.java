package com.converter.rate.service;

import com.converter.audit.domain.AuditAction;
import com.converter.audit.service.AuditService;
import com.converter.rate.domain.RateProviderType;
import com.converter.rate.domain.RateSource;
import com.converter.rate.dto.RateSourceResponse;
import com.converter.rate.repository.RateSourceRepository;
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
 * Permet a un administrateur de definir le taux manuel courant.
 *
 * <p>Reproduit exactement le mecanisme deja valide en Phase 1 pour
 * {@code exchange_rates} : cloturer la cotation courante puis en
 * inserer une nouvelle, dans la meme transaction. La contrainte SQL
 * {@code uq_rate_source_current} (migration V7) reste le filet de
 * securite ultime : deux publications concurrentes se serialisent
 * naturellement via le verrou de ligne pris par la cloture (voir
 * {@link com.converter.rate.repository.RateSourceRepository#closeCurrent}),
 * et si les deux tentaient malgre tout d'inserer une ligne "courante"
 * simultanement, la seconde echouerait en violation de contrainte
 * d'unicite plutot que de produire deux taux courants a la fois.
 * Aucun historique n'est jamais ecrase — {@code rate_sources} est
 * append-only.
 */
@Service
public class RateAdminService {

    private static final Logger log = LoggerFactory.getLogger(RateAdminService.class);

    private final RateSourceRepository rateSourceRepository;
    private final AuditService auditService;
    private final Clock clock;

    public RateAdminService(RateSourceRepository rateSourceRepository,
                            AuditService auditService,
                            Clock clock) {
        this.rateSourceRepository = rateSourceRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public RateSourceResponse publishManualRate(BigDecimal cfaPerCny, String note, String currencyPair, UUID actorId) {
        Instant now = clock.instant();

        // Cloture d'abord : cette UPDATE prend le verrou de ligne sur la
        // cotation courante (s'il en existe une), serialisant toute
        // creation de devis en cours qui la lirait en FOR SHARE.
        rateSourceRepository.closeCurrent(RateProviderType.MANUAL, currencyPair, now);

        RateSource source = new RateSource(
                RateProviderType.MANUAL, currencyPair, cfaPerCny, now, note, actorId, now);
        RateSource saved = rateSourceRepository.save(source);

        auditService.record(actorId, null, AuditAction.RATE_SOURCE_PUBLISHED,
                "RateSource", saved.getId().toString(),
                "{\"cfaPerCny\":\"" + cfaPerCny + "\",\"currencyPair\":\"" + currencyPair + "\"}");
        log.info("Nouveau taux manuel publie pour {} : {} par {}", currencyPair, cfaPerCny, actorId);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<RateSourceResponse> history(String currencyPair, Pageable pageable) {
        return rateSourceRepository
                .findByProviderTypeAndCurrencyPairOrderByEffectiveFromDesc(RateProviderType.MANUAL, currencyPair, pageable)
                .map(RateAdminService::toResponse);
    }

    private static RateSourceResponse toResponse(RateSource source) {
        return new RateSourceResponse(
                source.getId(),
                source.getProviderType(),
                source.getCurrencyPair(),
                source.getCfaPerCny(),
                source.getEffectiveFrom(),
                source.getEffectiveTo(),
                source.getNote(),
                source.getCreatedAt());
    }
}
