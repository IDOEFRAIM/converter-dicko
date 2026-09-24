package com.converter.quote.domain;

import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.rate.engine.PricingResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests unitaires purs de la machine d'etat du devis : aucun contexte
 * Spring, aucune base de donnees. Les limites de temps (30 minutes)
 * sont testees en passant des {@link Instant} explicites a
 * {@link Quote#accept} / {@link Quote#cancel}, sans jamais attendre en
 * conditions reelles.
 */
class QuoteTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-20T10:00:00Z");
    private static final Instant EXPIRES_AT = CREATED_AT.plusSeconds(30 * 60);

    @Test
    void accept_beforeExpiry_transitionsToAccepted() {
        Quote quote = newActiveQuote();

        quote.accept(EXPIRES_AT.minusSeconds(1));

        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.ACCEPTED);
        assertThat(quote.getAcceptedAt()).isEqualTo(EXPIRES_AT.minusSeconds(1));
    }

    @Test
    void accept_exactlyAtExpiryInstant_isConsideredExpired() {
        // now == expiresAt : la fenetre de 30 minutes est fermee (now
        // n'est plus strictement avant expiresAt).
        Quote quote = newActiveQuote();

        assertThatThrownBy(() -> quote.accept(EXPIRES_AT))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo(ErrorCode.QUOTE_EXPIRED));
        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.EXPIRED);
    }

    @Test
    void accept_afterExpiry_throwsAndPersistsExpiredStatus() {
        Quote quote = newActiveQuote();

        assertThatThrownBy(() -> quote.accept(EXPIRES_AT.plusSeconds(1)))
                .isInstanceOf(BusinessException.class);
        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.EXPIRED);
    }

    @Test
    void accept_aSecondTime_throwsInvalidState() {
        Quote quote = newActiveQuote();
        quote.accept(CREATED_AT.plusSeconds(60));

        assertThatThrownBy(() -> quote.accept(CREATED_AT.plusSeconds(120)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo(ErrorCode.INVALID_QUOTE_STATE));
    }

    @Test
    void cancel_anAcceptedQuote_throwsInvalidState() {
        Quote quote = newActiveQuote();
        quote.accept(CREATED_AT.plusSeconds(60));

        assertThatThrownBy(() -> quote.cancel(CREATED_AT.plusSeconds(120)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode()).isEqualTo(ErrorCode.INVALID_QUOTE_STATE));
        // L'acceptation n'est pas remise en cause par la tentative d'annulation.
        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.ACCEPTED);
    }

    @Test
    void cancel_beforeExpiry_transitionsToCancelled() {
        Quote quote = newActiveQuote();

        quote.cancel(CREATED_AT.plusSeconds(60));

        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.CANCELLED);
        assertThat(quote.getCancelledAt()).isEqualTo(CREATED_AT.plusSeconds(60));
    }

    @Test
    void acceptedQuote_financialSnapshotNeverChanges() {
        // Invariant central de la Phase 3 : aucune transition de statut
        // ne touche jamais aux colonnes financieres.
        Quote quote = newActiveQuote();
        BigDecimal originalCustomerRate = quote.getCustomerRate();
        BigDecimal originalAmountCny = quote.getAmountCny();

        quote.accept(CREATED_AT.plusSeconds(60));

        assertThat(quote.getCustomerRate()).isEqualByComparingTo(originalCustomerRate);
        assertThat(quote.getAmountCny()).isEqualByComparingTo(originalAmountCny);
    }

    @Test
    void effectiveStatus_reflectsExpiryWithoutMutatingPersistedStatus() {
        Quote quote = newActiveQuote();

        // Lecture pure : le statut persiste reste ACTIVE tant qu'aucune
        // transition n'a ete tentee, meme si l'instant observe est apres
        // expiresAt — seul le statut AFFICHE tient compte du temps ecoule.
        QuoteStatus displayed = quote.effectiveStatus(EXPIRES_AT.plusSeconds(1));

        assertThat(displayed).isEqualTo(QuoteStatus.EXPIRED);
        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.ACTIVE);
    }

    private static Quote newActiveQuote() {
        PricingResult pricing = new PricingResult(
                new BigDecimal("85.000000"), new BigDecimal("0.0000"), new BigDecimal("85.000000"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("100000.00"), new BigDecimal("100000.00"), new BigDecimal("1176.47"));
        return new Quote(UUID.randomUUID(), QuoteDirection.SEND_XOF, pricing, UUID.randomUUID(),
                CREATED_AT, EXPIRES_AT);
    }
}
