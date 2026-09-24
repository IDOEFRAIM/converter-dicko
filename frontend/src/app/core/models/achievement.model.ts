import { ExperienceProfile } from './user.model';

/**
 * "Mes gains" — miroir de `AchievementSummaryResponse` (backend) et de
 * `AchievementSummary` (mobile). Uniquement derive des ordres COMPLETED.
 * `badge*` est `null` pour un profil PRO (aucune gamification).
 */
export interface AchievementSummary {
  experienceProfile: ExperienceProfile;
  completedTransferCount: number;
  totalAmountXofCompleted: string | number;
  currentMonthAmountXofCompleted: string | number;
  poolsSucceededCount: number;
  xp: number;
  badgeCode: string | null;
  badgeLabel: string | null;
  nextBadgeLabel: string | null;
  transfersUntilNextBadge: number | null;
  currentBadgeThreshold: number | null;
  nextBadgeThreshold: number | null;
}

/** Avancement 0..1 dans le rang courant (meme calcul que `tierProgress` mobile). */
export function tierProgress(summary: AchievementSummary): number | null {
  if (!summary.badgeLabel) {
    return null;
  }
  if (summary.nextBadgeThreshold == null) {
    return 1;
  }
  const floor = summary.currentBadgeThreshold ?? 0;
  const span = summary.nextBadgeThreshold - floor;
  if (span <= 0) {
    return 1;
  }
  return Math.min(1, Math.max(0, (summary.completedTransferCount - floor) / span));
}
