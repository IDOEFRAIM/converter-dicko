import { ExperienceProfile } from '../models/user.model';

/**
 * Textes qui varient selon l'habillage — copie exacte de `ExperienceCopy`
 * (mobile/lib/core/theme/experience_theme.dart). Limite aux points de contact
 * les plus visibles (accueil) ; PRO garde le ton sobre.
 */
export const ExperienceCopy = {
  greeting(profile: ExperienceProfile, firstName: string): string {
    switch (profile) {
      case 'STUDENT_MALE':
        return `Pret pour de bonnes affaires, ${firstName} ?`;
      case 'STUDENT_FEMALE':
        return `Coucou ${firstName}`;
      default:
        return `Bonjour ${firstName}`;
    }
  },

  greetingEmoji(profile: ExperienceProfile): string {
    switch (profile) {
      case 'STUDENT_MALE':
        return '🔥';
      case 'STUDENT_FEMALE':
        return '✨';
      default:
        return '👋';
    }
  },

  homeRateEyebrow(profile: ExperienceProfile): string {
    switch (profile) {
      case 'STUDENT_MALE':
        return "TAUX OR AUJOURD'HUI";
      case 'STUDENT_FEMALE':
        return 'LE TAUX DU JOUR';
      default:
        return 'TAUX DU MOMENT';
    }
  },

  payCta(profile: ExperienceProfile): string {
    switch (profile) {
      case 'STUDENT_MALE':
        return 'Lancer le transfert';
      case 'STUDENT_FEMALE':
        return 'Envoyer mon transfert';
      default:
        return 'Payer un fournisseur';
    }
  },
} as const;
