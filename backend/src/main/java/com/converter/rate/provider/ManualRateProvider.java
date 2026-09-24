package com.converter.rate.provider;

import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.rate.domain.MarketRate;
import com.converter.rate.domain.RateProviderType;
import com.converter.rate.domain.RateSource;
import com.converter.rate.repository.RateSourceRepository;
import com.converter.settings.domain.SettingKey;
import com.converter.settings.service.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Source de cotation MVP : un taux saisi manuellement par un
 * administrateur, publie via {@link com.converter.rate.service.RateAdminService}.
 *
 * <p>Seule implementation active de {@link RateProvider} a ce stade —
 * voir la note de l'interface pour les sources futures.
 *
 * <p><b>Politique de fraicheur</b> (passe 2, P2-7) : une cotation avec {@code effective_to IS
 * NULL} est <b>CURRENT</b> tant que son age (mesure sur {@code effective_from}) ne depasse pas
 * {@code RATE_MAX_AGE_MINUTES}. Au-dela, elle est <b>STALE</b> et traitee comme
 * <b>UNAVAILABLE</b> (rejet + journalisation {@code WARN}) : mieux vaut refuser un devis que le
 * pricer sur un taux perime. {@code RATE_MAX_AGE_MINUTES = 0} desactive entierement ce controle
 * (comportement anterieur). Ce provider ne remonte jamais vers {@code quote}/{@code order} — il
 * ne depend que de la config et de l'horloge.
 */
@Component
public class ManualRateProvider implements RateProvider {

    private static final Logger log = LoggerFactory.getLogger(ManualRateProvider.class);

    private final RateSourceRepository rateSourceRepository;
    private final SettingsService settingsService;
    private final Clock clock;

    public ManualRateProvider(RateSourceRepository rateSourceRepository,
                              SettingsService settingsService,
                              Clock clock) {
        this.rateSourceRepository = rateSourceRepository;
        this.settingsService = settingsService;
        this.clock = clock;
    }

    @Override
    public MarketRate currentRate(String currencyPair) {
        RateSource current = rateSourceRepository
                .findCurrentForPricing(RateProviderType.MANUAL, currencyPair)
                .orElseThrow(() -> new BusinessException(ErrorCode.RATE_SOURCE_UNAVAILABLE,
                        "Aucun taux manuel n'est configure pour " + currencyPair
                                + ". Un administrateur doit d'abord publier un taux."));
        if (isStale(current)) {
            log.warn("Cotation {} {} perimee (publiee le {}, age > RATE_MAX_AGE_MINUTES) : pricing refuse.",
                    RateProviderType.MANUAL, currencyPair, current.getEffectiveFrom());
            throw new BusinessException(ErrorCode.RATE_SOURCE_UNAVAILABLE,
                    "Le taux courant pour " + currencyPair + " est perime. Un administrateur doit publier "
                            + "une cotation a jour avant tout nouveau devis.");
        }
        return current.toMarketRate();
    }

    @Override
    public boolean isAvailable(String currencyPair) {
        // Lecture non verrouillee, volontairement distincte de findCurrentForPricing (FOR SHARE) :
        // une simple sonde de disponibilite ne doit jamais entrer en contention avec un calcul de
        // pricing en cours ni avec une publication administrative.
        return rateSourceRepository
                .findByProviderTypeAndCurrencyPairAndEffectiveToIsNull(RateProviderType.MANUAL, currencyPair)
                .filter(source -> !isStale(source))
                .isPresent();
    }

    @Override
    public RateProviderType type() {
        return RateProviderType.MANUAL;
    }

    /** {@code true} si une politique de fraicheur est active et que la cotation la depasse. */
    private boolean isStale(RateSource source) {
        int maxAgeMinutes = settingsService.getInt(SettingKey.RATE_MAX_AGE_MINUTES);
        if (maxAgeMinutes <= 0) {
            return false;
        }
        Instant threshold = clock.instant().minus(Duration.ofMinutes(maxAgeMinutes));
        return source.getEffectiveFrom().isBefore(threshold);
    }
}
