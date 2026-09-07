package com.converter.notification.service;

import com.converter.notification.domain.NotificationType;
import com.converter.user.domain.ExperienceProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Logique pure de personnalisation des titres de notification (voir {@link NotificationCopy}) —
 * aucun contexte Spring, aucune base : {@code NotificationServiceIT} verifie separement le
 * cablage reel (lecture du profil utilisateur en base).
 */
class NotificationCopyTest {

    @ParameterizedTest
    @EnumSource(NotificationType.class)
    void pro_neverGetsAPersonalizedTitle(NotificationType type) {
        assertThat(NotificationCopy.title(type, ExperienceProfile.PRO, "Titre neutre")).isEqualTo("Titre neutre");
    }

    @ParameterizedTest
    @EnumSource(value = ExperienceProfile.class, names = {"STUDENT_MALE", "STUDENT_FEMALE"})
    void nonCelebratedTypes_keepTheDefaultTitle_forEveryProfile(ExperienceProfile profile) {
        for (NotificationType type : new NotificationType[] {
                NotificationType.QUOTE_CREATED,
                NotificationType.PAYMENT_SUBMITTED,
                NotificationType.EXCHANGE_STARTED,
                NotificationType.EXCHANGE_PROGRESS,
                NotificationType.PREFERRED_RATE_REACHED,
                NotificationType.PREFERRED_RATE_EXPIRED,
                NotificationType.EXCHANGE_CANCELLED,
                NotificationType.ORDER_EXPIRED,
                NotificationType.POOL_EXPIRED,
        }) {
            assertThat(NotificationCopy.title(type, profile, "Titre neutre")).isEqualTo("Titre neutre");
        }
    }

    @Test
    void studentMale_getsDistinctTitles_forCelebratedTypes() {
        assertThat(NotificationCopy.title(NotificationType.RATE_ALERT_TRIGGERED, ExperienceProfile.STUDENT_MALE, "Objectif de taux atteint"))
                .isNotEqualTo("Objectif de taux atteint");
        assertThat(NotificationCopy.title(NotificationType.POOL_SUCCEEDED, ExperienceProfile.STUDENT_MALE, "Objectif atteint !"))
                .isNotEqualTo("Objectif atteint !");
        assertThat(NotificationCopy.title(NotificationType.PAYMENT_CONFIRMED, ExperienceProfile.STUDENT_MALE, "Paiement confirme"))
                .isNotEqualTo("Paiement confirme");
        assertThat(NotificationCopy.title(NotificationType.EXCHANGE_COMPLETED, ExperienceProfile.STUDENT_MALE, "Echange termine"))
                .isNotEqualTo("Echange termine");
    }

    @Test
    void studentFemale_getsItsOwnDistinctTitles_differentFromStudentMale() {
        for (NotificationType type : new NotificationType[] {
                NotificationType.RATE_ALERT_TRIGGERED,
                NotificationType.POOL_SUCCEEDED,
                NotificationType.PAYMENT_CONFIRMED,
                NotificationType.EXCHANGE_COMPLETED,
        }) {
            String maleTitle = NotificationCopy.title(type, ExperienceProfile.STUDENT_MALE, "Neutre");
            String femaleTitle = NotificationCopy.title(type, ExperienceProfile.STUDENT_FEMALE, "Neutre");
            assertThat(femaleTitle).isNotEqualTo("Neutre");
            assertThat(femaleTitle).isNotEqualTo(maleTitle);
        }
    }
}
