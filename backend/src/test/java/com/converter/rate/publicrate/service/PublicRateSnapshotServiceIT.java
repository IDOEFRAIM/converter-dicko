package com.converter.rate.publicrate.service;

import com.converter.common.exception.BusinessException;
import com.converter.quote.dto.CreateQuoteRequest;
import com.converter.quote.domain.QuoteDirection;
import com.converter.quote.dto.QuoteResponse;
import com.converter.rate.publicrate.domain.PublicRateSnapshot;
import com.converter.rate.publicrate.dto.PublicRateHistoryEntry;
import com.converter.rate.publicrate.repository.PublicRateSnapshotRepository;
import com.converter.settings.domain.SettingKey;
import com.converter.support.AbstractRateQuoteIT;
import com.converter.user.domain.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 5 — historique public du taux client. Verifie que {@code PublicRateSnapshotService}
 * calcule le {@code customerRate} avec {@link com.converter.rate.engine.RateEngine} (jamais une
 * formule dupliquee), qu'un snapshot deja publie n'est <b>jamais</b> recalcule apres coup, et que
 * la separation avec le pricing interne/les devis reste etanche.
 */
class PublicRateSnapshotServiceIT extends AbstractRateQuoteIT {

    @Autowired
    private PublicRateSnapshotService publicRateSnapshotService;

    @Autowired
    private PublicRateSnapshotRepository publicRateSnapshotRepository;

    // ---- 1 / 3 / 4 : insertion, precision BigDecimal, timestamp ----

    @Test
    void record_persistsCustomerRateWithFullPrecisionAndUtcTimestamp() {
        // Deja precision microseconde (TIMESTAMPTZ) : round-trip exact attendu, sans troncature.
        Instant recordedAt = Instant.parse("2026-09-03T14:00:00.123456Z");

        publicRateSnapshotService.record(new BigDecimal("83.000000"), "XOF/CNY", recordedAt);

        PublicRateSnapshot persisted = publicRateSnapshotRepository.findAll().stream()
                .filter(s -> s.getRecordedAt().equals(recordedAt))
                .findFirst().orElseThrow();
        assertThat(persisted.getCustomerRate()).isEqualByComparingTo("83.000000");
        assertThat(persisted.getCurrencyPair()).isEqualTo("XOF/CNY");
    }

    // ---- 2 / 5 / 6 : plusieurs snapshots, tri DESC, pagination ----

    @Test
    void history_returnsSnapshotsNewestFirst_withDeterministicTieBreak() {
        // Bornes from/to serrees autour des 3 valeurs inserees : la requete reste precise et
        // ignore tout le reste de la table (partagee par toute la suite d'integration), quelle
        // que soit la quantite d'autres snapshots XOF/CNY deja accumulee par d'autres tests.
        // Fenetre FIXE et unique dans toute la suite : les autres tests horodatent soit avec
        // l'horloge reelle (~maintenant), soit sur d'autres dates fixes (2020-01-01, 2026-xx) —
        // aucun de leurs snapshots XOF/CNY ne peut tomber sur cette journee de mars 2019.
        // Une fenetre relative "il y a 5 minutes" etait chevauchee par la duree de la suite.
        Instant t0 = Instant.parse("2019-03-14T00:00:00Z");
        publicRateSnapshotService.record(new BigDecimal("80.000000"), "XOF/CNY", t0);
        publicRateSnapshotService.record(new BigDecimal("81.000000"), "XOF/CNY", t0.plusSeconds(60));
        publicRateSnapshotService.record(new BigDecimal("82.000000"), "XOF/CNY", t0.plusSeconds(120));

        var page = publicRateSnapshotService.history("XOF/CNY", t0, t0.plusSeconds(120), PageRequest.of(0, 10));

        assertThat(page.content()).extracting(PublicRateHistoryEntry::customerRate).containsExactly(
                new BigDecimal("82.000000"), new BigDecimal("81.000000"), new BigDecimal("80.000000"));
    }

    @Test
    void history_isPaginated_neverReturnsTheWholeTableAtOnce() {
        Instant t0 = Instant.now().minusSeconds(600);
        for (int i = 0; i < 5; i++) {
            publicRateSnapshotService.record(new BigDecimal("70.00000" + i), "XOF/CNY", t0.plusSeconds(i));
        }

        var page = publicRateSnapshotService.history("XOF/CNY", null, null, PageRequest.of(0, 2));

        assertThat(page.content()).hasSize(2);
        assertThat(page.size()).isEqualTo(2);
    }

    // ---- 7 : filtre pair ----

    @Test
    void repositorySearch_filtersByPair_neverMixesOtherPairs() {
        // Contourne volontairement le service (dont la liste blanche de paires est testee a
        // part, ci-dessous) pour prouver que la requete elle-meme filtre correctement par paire
        // — le schema/l'index sont deja prepares pour plusieurs paires (section 12), meme si
        // l'API publique n'en valide qu'une seule aujourd'hui.
        publicRateSnapshotRepository.save(new PublicRateSnapshot("OTHER/PAIR", new BigDecimal("60.000000"), Instant.now()));
        publicRateSnapshotRepository.save(new PublicRateSnapshot("XOF/CNY", new BigDecimal("61.000000"), Instant.now()));

        var page = publicRateSnapshotRepository.search("OTHER/PAIR", false, null, false, null, PageRequest.of(0, 20));

        assertThat(page.getContent()).isNotEmpty();
        assertThat(page.getContent()).allSatisfy(s -> assertThat(s.getCurrencyPair()).isEqualTo("OTHER/PAIR"));
    }

    @Test
    void history_unsupportedPair_returnsValidationError() {
        assertThatThrownBy(() -> publicRateSnapshotService.history("USD/EUR", null, null, PageRequest.of(0, 20)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode().name()).isEqualTo("VALIDATION_ERROR"));
    }

    // ---- 8 : filtre date ----

    @Test
    void history_filtersByDateRange() {
        Instant base = Instant.parse("2020-01-01T00:00:00Z");
        publicRateSnapshotService.record(new BigDecimal("50.000000"), "XOF/CNY", base);
        publicRateSnapshotService.record(new BigDecimal("51.000000"), "XOF/CNY", base.plusSeconds(3600));
        publicRateSnapshotService.record(new BigDecimal("52.000000"), "XOF/CNY", base.plusSeconds(7200));

        var page = publicRateSnapshotService.history("XOF/CNY", base.plusSeconds(1), base.plusSeconds(7199),
                PageRequest.of(0, 20));

        assertThat(page.content()).extracting(PublicRateHistoryEntry::customerRate)
                .containsExactly(new BigDecimal("51.000000"));
    }

    // ---- Confidentialite : le DTO public ne porte structurellement que 3 champs ----

    @Test
    void publicDto_neverExposesInternalPricingFields() {
        var fields = PublicRateHistoryEntry.class.getRecordComponents();
        assertThat(fields).hasSize(3);
        assertThat(fields).extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactlyInAnyOrder("currencyPair", "customerRate", "recordedAt");
    }

    // ---- Test critique (section 6/22/23) : un changement de marge ne modifie jamais un ancien snapshot ----

    @Test
    void changingMarginBetweenTwoPublications_neverRecomputesTheEarlierSnapshot() {
        resetMarginToZero();
        String admin = adminToken();

        publishCostRate(admin, "85.000000");
        var afterFirst = publicRateSnapshotService.history("XOF/CNY", null, null, PageRequest.of(0, 1));
        BigDecimal snapshotA = afterFirst.content().get(0).customerRate();
        assertThat(snapshotA).isEqualByComparingTo("85.000000");

        settingsService.update(SettingKey.DEFAULT_MARGIN_PERCENTAGE, "5", createUser(RoleCode.ADMIN).getId());
        publishCostRate(admin, "85.000000");
        var afterSecond = publicRateSnapshotService.history("XOF/CNY", null, null, PageRequest.of(0, 2));
        BigDecimal snapshotB = afterSecond.content().get(0).customerRate();
        BigDecimal snapshotAReread = afterSecond.content().get(1).customerRate();

        assertThat(snapshotB).isEqualByComparingTo("89.250000"); // 85 * 1.05
        assertThat(snapshotAReread).isEqualByComparingTo("85.000000"); // inchange
    }

    // ---- Section 26 : compatibilite avec Quote — une publication ulterieure n'affecte pas un Quote deja cree ----

    @Test
    void publishingANewRate_neverAltersAnAlreadyCreatedQuotesCustomerRate() {
        resetMarginToZero();
        String admin = adminToken();
        publishRate(admin, "84.200000");
        String user = tokenFor(createUser(RoleCode.USER));

        QuoteResponse quote = createQuote(user, new CreateQuoteRequest(QuoteDirection.SEND_XOF,
                new BigDecimal("100000"), null));
        assertThat(quote.customerRate()).isEqualByComparingTo("84.200000");

        publishRate(admin, "83.900000");

        QuoteResponse reloaded = restTemplateReloadQuote(user, quote.id());
        assertThat(reloaded.customerRate()).isEqualByComparingTo("84.200000");
    }

    private QuoteResponse restTemplateReloadQuote(String userToken, java.util.UUID quoteId) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(userToken);
        return restTemplate.exchange("/api/v1/quotes/" + quoteId, org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers),
                new org.springframework.core.ParameterizedTypeReference<com.converter.common.api.ApiResponse<QuoteResponse>>() {
                }).getBody().data();
    }

    // ---- Section 25 : deux publications concurrentes -> deux snapshots coherents, sans perte ----

    @Test
    void twoConcurrentPublications_bothProduceASnapshot_noneLost() throws Exception {
        resetMarginToZero();
        String admin = adminToken();
        long before = publicRateSnapshotRepository.count();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> f1 = executor.submit(() -> publishCostRate(admin, "77.000000"));
            Future<?> f2 = executor.submit(() -> publishCostRate(admin, "78.000000"));
            f1.get(30, TimeUnit.SECONDS);
            f2.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdown();
        }

        long after = publicRateSnapshotRepository.count();
        assertThat(after - before).isEqualTo(2);
    }
}
