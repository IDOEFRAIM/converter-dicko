package com.converter.rate.provider;

import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.rate.domain.MarketRate;
import com.converter.rate.domain.RateProviderType;
import com.converter.rate.domain.RateSource;
import com.converter.rate.repository.RateSourceRepository;
import com.converter.settings.domain.SettingKey;
import com.converter.settings.service.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link ManualRateProvider}, avec un {@link RateSourceRepository} et un
 * {@link SettingsService} entierement simules (Mockito) : verifie de facon deterministe le cas
 * "aucun taux configure" ({@code 503 RATE_SOURCE_UNAVAILABLE}), le mapping vers {@link MarketRate},
 * et la politique de fraicheur ({@code RATE_MAX_AGE_MINUTES}).
 */
@ExtendWith(MockitoExtension.class)
class ManualRateProviderTest {

    @Mock
    private RateSourceRepository rateSourceRepository;

    @Mock
    private SettingsService settingsService;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-02T12:00:00Z"), ZoneOffset.UTC);

    private ManualRateProvider provider;

    @BeforeEach
    void setUp() {
        provider = new ManualRateProvider(rateSourceRepository, settingsService, clock);
        // Par defaut : pas de controle de fraicheur (RATE_MAX_AGE_MINUTES = 0).
        lenient().when(settingsService.getInt(SettingKey.RATE_MAX_AGE_MINUTES)).thenReturn(0);
    }

    @Test
    void currentRate_withNoConfiguredRate_throwsRateSourceUnavailable() {
        when(rateSourceRepository.findCurrentForPricing(RateProviderType.MANUAL, "XOF/CNY"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> provider.currentRate("XOF/CNY"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode())
                        .isEqualTo(ErrorCode.RATE_SOURCE_UNAVAILABLE));
    }

    @Test
    void currentRate_withConfiguredRate_mapsToMarketRate() {
        RateSource source = rateSourceOf(new BigDecimal("85.000000"), clock.instant());
        when(rateSourceRepository.findCurrentForPricing(RateProviderType.MANUAL, "XOF/CNY"))
                .thenReturn(Optional.of(source));

        MarketRate rate = provider.currentRate("XOF/CNY");

        assertThat(rate.currencyPair()).isEqualTo("XOF/CNY");
        assertThat(rate.cfaPerCny()).isEqualByComparingTo("85.000000");
        assertThat(rate.source()).isEqualTo(RateProviderType.MANUAL);
        assertThat(rate.rateSourceId()).isEqualTo(source.getId());
    }

    @Test
    void type_isManual() {
        assertThat(provider.type()).isEqualTo(RateProviderType.MANUAL);
    }

    @Test
    void isAvailable_withNoConfiguredRate_returnsFalseWithoutThrowing() {
        when(rateSourceRepository.findByProviderTypeAndCurrencyPairAndEffectiveToIsNull(
                RateProviderType.MANUAL, "XOF/CNY")).thenReturn(Optional.empty());

        assertThat(provider.isAvailable("XOF/CNY")).isFalse();
    }

    @Test
    void isAvailable_withConfiguredRate_returnsTrue() {
        when(rateSourceRepository.findByProviderTypeAndCurrencyPairAndEffectiveToIsNull(
                RateProviderType.MANUAL, "XOF/CNY"))
                .thenReturn(Optional.of(rateSourceOf(new BigDecimal("85"), clock.instant())));

        assertThat(provider.isAvailable("XOF/CNY")).isTrue();
    }

    @Test
    void currentRate_whenRateOlderThanMaxAge_isRejectedAsStale() {
        when(settingsService.getInt(SettingKey.RATE_MAX_AGE_MINUTES)).thenReturn(60);
        // Publiee il y a 2 h, plafond 60 min => perimee.
        RateSource stale = rateSourceOf(new BigDecimal("85"), clock.instant().minusSeconds(7200));
        when(rateSourceRepository.findCurrentForPricing(RateProviderType.MANUAL, "XOF/CNY"))
                .thenReturn(Optional.of(stale));

        assertThatThrownBy(() -> provider.currentRate("XOF/CNY"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode())
                        .isEqualTo(ErrorCode.RATE_SOURCE_UNAVAILABLE));
    }

    @Test
    void isAvailable_whenRateOlderThanMaxAge_returnsFalse() {
        when(settingsService.getInt(SettingKey.RATE_MAX_AGE_MINUTES)).thenReturn(60);
        RateSource stale = rateSourceOf(new BigDecimal("85"), clock.instant().minusSeconds(7200));
        when(rateSourceRepository.findByProviderTypeAndCurrencyPairAndEffectiveToIsNull(
                RateProviderType.MANUAL, "XOF/CNY")).thenReturn(Optional.of(stale));

        assertThat(provider.isAvailable("XOF/CNY")).isFalse();
    }

    @Test
    void currentRate_whenRateWithinMaxAge_isAccepted() {
        when(settingsService.getInt(SettingKey.RATE_MAX_AGE_MINUTES)).thenReturn(60);
        // Publiee il y a 30 min, plafond 60 min => encore courante.
        RateSource fresh = rateSourceOf(new BigDecimal("85"), clock.instant().minusSeconds(1800));
        when(rateSourceRepository.findCurrentForPricing(RateProviderType.MANUAL, "XOF/CNY"))
                .thenReturn(Optional.of(fresh));

        assertThat(provider.currentRate("XOF/CNY").cfaPerCny()).isEqualByComparingTo("85");
    }

    private static RateSource rateSourceOf(BigDecimal cfaPerCny, Instant effectiveFrom) {
        RateSource source = new RateSource(RateProviderType.MANUAL, "XOF/CNY", cfaPerCny,
                effectiveFrom, "test", UUID.randomUUID(), effectiveFrom);
        source.setId(UUID.randomUUID());
        return source;
    }
}
