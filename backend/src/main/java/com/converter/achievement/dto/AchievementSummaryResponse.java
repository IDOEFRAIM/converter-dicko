package com.converter.achievement.dto;

import com.converter.user.domain.ExperienceProfile;

import java.math.BigDecimal;

/**
 * Vue "Mes gains" (mission "differenciation marketing", Lot 2). Purement une photographie
 * d'activite deja realisee — jamais un recalcul de taux/frais, jamais une donnee inventee (le
 * "volume transfere" est le seul chiffre honnete disponible sans une reference externe de
 * comparaison ; jamais presente comme une "economie" par rapport a un concurrent, qui exigerait
 * une donnee que ce backend n'a pas).
 *
 * <p>{@code badgeCode}/{@code badgeLabel} sont {@code null} pour {@code PRO} (aucune gamification,
 * voir la Javadoc de classe de {@code AchievementService}) et pour un profil STUDENT_* qui n'a
 * encore aucun ordre {@code COMPLETED}. {@code nextBadgeLabel}/{@code transfersUntilNextBadge}
 * sont {@code null} des que le palier maximal est atteint, ou pour {@code PRO}.
 *
 * <p>{@code currentBadgeThreshold}/{@code nextBadgeThreshold} : nombre d'ordres {@code COMPLETED}
 * qui borne le palier courant et le suivant — permet a l'affichage mobile de tracer une
 * progression honnete ("X sur N transferts dans ce rang") sans repliquer le bareme cote client.
 * {@code null} avec la meme logique que ci-dessus (PRO, aucun badge, ou palier maximal atteint
 * pour {@code nextBadgeThreshold}).
 */
public record AchievementSummaryResponse(
        ExperienceProfile experienceProfile,
        long completedTransferCount,
        BigDecimal totalAmountXofCompleted,
        BigDecimal currentMonthAmountXofCompleted,
        long poolsSucceededCount,
        long xp,
        String badgeCode,
        String badgeLabel,
        String nextBadgeLabel,
        Long transfersUntilNextBadge,
        Long currentBadgeThreshold,
        Long nextBadgeThreshold
) {
}
