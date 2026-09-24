package com.converter.achievement.service;

import com.converter.user.domain.ExperienceProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Logique pure de progression des badges (voir {@link AchievementService#resolveBadgeProgress}) —
 * aucun contexte Spring, aucune base : chaque palier/frontiere est verifie a moindre cout,
 * contrairement a {@code AchievementServiceIT} qui verifie seulement le cablage reel (ordres ->
 * agregation -> reponse) avec une poignee d'ordres.
 */
class AchievementServiceTest {

    @Test
    void pro_neverHasABadge_regardlessOfActivity() {
        for (long count : new long[] {0, 1, 30, 1000}) {
            var progress = AchievementService.resolveBadgeProgress(ExperienceProfile.PRO, count);
            assertThat(progress.code()).isNull();
            assertThat(progress.label()).isNull();
            assertThat(progress.nextLabel()).isNull();
            assertThat(progress.transfersUntilNext()).isNull();
        }
    }

    @ParameterizedTest
    @CsvSource({
            "0,,Debutant,1",
            "1,DEBUTANT,Habitue,4",
            "4,DEBUTANT,Habitue,1",
            "5,HABITUE,Expert,10",
            "14,HABITUE,Expert,1",
            "15,EXPERT,Champion,15",
            "29,EXPERT,Champion,1",
    })
    void nonPro_progressesThroughTheChangerLadder(long count, String expectedCode, String expectedNextLabel,
                                                  long expectedTransfersUntilNext) {
        var progress = AchievementService.resolveBadgeProgress(ExperienceProfile.STUDENT_MALE, count);
        assertThat(progress.code()).isEqualTo(expectedCode);
        assertThat(progress.nextLabel()).isEqualTo(expectedNextLabel);
        assertThat(progress.transfersUntilNext()).isEqualTo(expectedTransfersUntilNext);
    }

    @Test
    void nonPro_atMaxTier_hasNoNextBadge() {
        var progress = AchievementService.resolveBadgeProgress(ExperienceProfile.STUDENT_MALE, 30);
        assertThat(progress.code()).isEqualTo("CHAMPION");
        assertThat(progress.label()).isEqualTo("Champion");
        assertThat(progress.nextLabel()).isNull();
        assertThat(progress.transfersUntilNext()).isNull();

        // Au-dela du seuil maximal, le palier reste le dernier -- jamais un palier "hors catalogue".
        var farBeyond = AchievementService.resolveBadgeProgress(ExperienceProfile.STUDENT_MALE, 500);
        assertThat(farBeyond.code()).isEqualTo("CHAMPION");
    }

    @Test
    void everyNonProProfileSharesTheExactSameLadder() {
        for (long count : new long[] {0, 1, 7, 20, 40}) {
            var male = AchievementService.resolveBadgeProgress(ExperienceProfile.STUDENT_MALE, count);
            var female = AchievementService.resolveBadgeProgress(ExperienceProfile.STUDENT_FEMALE, count);
            assertThat(female.code()).isEqualTo(male.code());
            assertThat(female.label()).isEqualTo(male.label());
            assertThat(female.nextLabel()).isEqualTo(male.nextLabel());
            assertThat(female.transfersUntilNext()).isEqualTo(male.transfersUntilNext());
        }
    }
}
