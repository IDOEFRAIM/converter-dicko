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
            "0,,Guerrier,1",
            "1,GUERRIER,Batisseur,4",
            "4,GUERRIER,Batisseur,1",
            "5,BATISSEUR,Commandant,10",
            "14,BATISSEUR,Commandant,1",
            "15,COMMANDANT,Legende,15",
            "29,COMMANDANT,Legende,1",
    })
    void studentMale_progressesThroughTiers(long count, String expectedCode, String expectedNextLabel,
                                            long expectedTransfersUntilNext) {
        var progress = AchievementService.resolveBadgeProgress(ExperienceProfile.STUDENT_MALE, count);
        assertThat(progress.code()).isEqualTo(expectedCode);
        assertThat(progress.nextLabel()).isEqualTo(expectedNextLabel);
        assertThat(progress.transfersUntilNext()).isEqualTo(expectedTransfersUntilNext);
    }

    @Test
    void studentMale_atMaxTier_hasNoNextBadge() {
        var progress = AchievementService.resolveBadgeProgress(ExperienceProfile.STUDENT_MALE, 30);
        assertThat(progress.code()).isEqualTo("LEGENDE");
        assertThat(progress.label()).isEqualTo("Legende");
        assertThat(progress.nextLabel()).isNull();
        assertThat(progress.transfersUntilNext()).isNull();

        // Au-dela du seuil maximal, le palier reste LEGENDE -- jamais un palier "hors catalogue".
        var farBeyond = AchievementService.resolveBadgeProgress(ExperienceProfile.STUDENT_MALE, 500);
        assertThat(farBeyond.code()).isEqualTo("LEGENDE");
    }

    @ParameterizedTest
    @CsvSource({
            "0,,Eclaireuse,1",
            "1,ECLAIREUSE,Protectrice,4",
            "5,PROTECTRICE,Ambassadrice,10",
            "15,AMBASSADRICE,,",
    })
    void studentFemale_progressesThroughItsOwnDistinctTiers(long count, String expectedCode, String expectedNextLabel,
                                                             Long expectedTransfersUntilNext) {
        var progress = AchievementService.resolveBadgeProgress(ExperienceProfile.STUDENT_FEMALE, count);
        assertThat(progress.code()).isEqualTo(expectedCode);
        assertThat(progress.nextLabel()).isEqualTo(expectedNextLabel);
        assertThat(progress.transfersUntilNext()).isEqualTo(expectedTransfersUntilNext);
    }
}
