package com.converter.notification.service;

import com.converter.notification.domain.NotificationType;
import com.converter.user.domain.ExperienceProfile;

/**
 * Titres de notification differencies par {@link ExperienceProfile} (mission "differenciation
 * marketing", section "Notifications" : "Ex: pour un PRO : 'Votre transaction est confirmee.'
 * Pour un STUDENT MALE : 'Taux OR ! Lance un defi maintenant !'").
 *
 * <p>Volontairement limite au <b>titre</b> — jamais au corps du message, qui reste toujours celui
 * calcule par le service metier appelant (montants, references, dates : une donnee reelle que
 * cette classe n'a pas et ne doit jamais re-deriver). Volontairement limite aux quatre types de
 * notification les plus visibles/celebrables (meme discipline de perimetre que la
 * differenciation de textes deja appliquee cote mobile pour l'accueil) : toutes les autres
 * notifications (paiement declare, devis cree, ordre expire...) gardent le ton sobre unique
 * existant pour tous les profils, jamais reecrites.
 *
 * <p>{@code PRO} ne recoit jamais de variante : {@code defaultTitle} lui est toujours renvoye tel
 * quel (meme discipline que {@link com.converter.achievement.service.AchievementService}, qui ne
 * gamifie jamais ce profil). Pure, sans dependance Spring — testable unitairement.
 */
final class NotificationCopy {

    private NotificationCopy() {
    }

    static String title(NotificationType type, ExperienceProfile profile, String defaultTitle) {
        if (profile == ExperienceProfile.PRO) {
            return defaultTitle;
        }
        return switch (type) {
            case RATE_ALERT_TRIGGERED -> profile == ExperienceProfile.STUDENT_MALE
                    ? "🔥 Taux OR atteint ! Fonce."
                    : "✨ Ton taux ideal vient d'arriver";
            case POOL_SUCCEEDED -> profile == ExperienceProfile.STUDENT_MALE
                    ? "🏆 Victoire ! Objectif de la Ruee ecrase"
                    : "💛 Vous avez reussi, ensemble";
            case PAYMENT_CONFIRMED -> profile == ExperienceProfile.STUDENT_MALE
                    ? "✅ Paiement valide, mission en cours"
                    : "✨ Ton paiement est confirme";
            case EXCHANGE_COMPLETED -> profile == ExperienceProfile.STUDENT_MALE
                    ? "🔥 Echange termine, mission accomplie"
                    : "🎉 Ton echange est termine";
            default -> defaultTitle;
        };
    }
}
