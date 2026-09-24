/** "Ruee collective" — miroir de `PoolResponse` / `PoolParticipantResponse` (backend, mobile). */
export type PoolStatus = 'ACTIVE' | 'SUCCEEDED' | 'EXPIRED' | 'CANCELLED';

type Decimal = string | number;

export interface Pool {
  id: string;
  code: string;
  creatorId: string;
  currencyPair: string;
  targetAmountXof: Decimal;
  currentAmountXof: Decimal;
  status: PoolStatus;
  participantCount: number;
  rewardMarginReductionPercentage: Decimal;
  rewardBasePercentage: Decimal;
  rewardParticipantBonusPercentage: Decimal;
  rewardVolumeBonusPercentage: Decimal;
  rewardPerParticipantPercentage: Decimal;
  rewardPerMillionXofPercentage: Decimal;
  rewardMaxPercentage: Decimal;
  createdAt: string;
  expiresAt: string;
  succeededAt: string | null;
  expiredAt: string | null;
  cancelledAt: string | null;
  viewerIsParticipant: boolean;
  viewerIsCreator: boolean;
}

export interface PoolParticipant {
  userId: string;
  firstName: string;
  isCreator: boolean;
  joinedAt: string;
  contributedAmountXof: Decimal;
}

export interface CreatePoolRequest {
  targetAmountXof: string;
  durationMinutes: number;
}

/** Progression 0..1 (affichage uniquement). */
export function poolProgress(pool: Pool): number {
  const target = Number(pool.targetAmountXof) || 0;
  const current = Number(pool.currentAmountXof) || 0;
  return target <= 0 ? 0 : Math.min(1, Math.max(0, current / target));
}

/** "0.500" -> "0.5" — purement cosmetique, comme `_fmtPct` mobile. */
export function formatPoints(raw: Decimal): string {
  const s = String(raw);
  return s.includes('.') ? s.replace(/\.?0+$/, '') : s;
}
