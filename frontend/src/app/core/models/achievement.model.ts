export type ExperienceProfile = 'PRO' | 'STUDENT_MALE' | 'STUDENT_FEMALE';

/**
 * "Mes gains" (mission "differenciation marketing", Lot 2) — miroir de `AchievementSummaryResponse`.
 * Purement une photographie d'activite deja realisee (ordres COMPLETED), jamais un recalcul de
 * taux/frais ni une donnee inventee. `badgeCode`/`badgeLabel` sont `null` pour `PRO` (aucune
 * gamification) et pour un profil STUDENT_* sans aucun ordre COMPLETED encore.
 * `currentBadgeThreshold`/`nextBadgeThreshold` : nombre d'ordres COMPLETED qui borne le palier
 * courant et le suivant, pour tracer une progression honnete sans repliquer le bareme cote client.
 */
export interface AchievementSummary {
  experienceProfile: ExperienceProfile;
  completedTransferCount: number;
  totalAmountXofCompleted: string;
  currentMonthAmountXofCompleted: string;
  poolsSucceededCount: number;
  xp: number;
  badgeCode: string | null;
  badgeLabel: string | null;
  nextBadgeLabel: string | null;
  transfersUntilNextBadge: number | null;
  currentBadgeThreshold: number | null;
  nextBadgeThreshold: number | null;
}

export function achievementHasBadge(summary: AchievementSummary): boolean {
  return summary.badgeLabel !== null;
}

/** Avancement 0..1 dans le rang courant vers le suivant. `null` si non applicable (PRO / aucun
 * transfert) ; `1` au palier maximal. */
export function achievementTierProgress(summary: AchievementSummary): number | null {
  if (!achievementHasBadge(summary)) return null;
  if (summary.nextBadgeThreshold === null) return 1;
  const floor = summary.currentBadgeThreshold ?? 0;
  const span = summary.nextBadgeThreshold - floor;
  if (span <= 0) return 1;
  return Math.min(Math.max((summary.completedTransferCount - floor) / span, 0), 1);
}
