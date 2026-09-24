/**
 * "Ruee collective" (mission "differenciation marketing", Lot 3) — miroir de
 * `com.converter.pool.dto.PoolResponse` / `PoolParticipantResponse`. La contribution elle-meme
 * passe par `CreateOrderRequest.poolId` (voir order.model.ts), jamais un endpoint dedie ici.
 */
export type PoolStatus = 'ACTIVE' | 'SUCCEEDED' | 'EXPIRED' | 'CANCELLED';

/**
 * `viewerIsParticipant`/`viewerIsCreator` sont relatifs au compte connecte — n'importe quel
 * compte peut consulter une Ruee (previsualisation avant de la rejoindre), pas seulement ses
 * participants. `rewardMarginReductionPercentage` est le rabais calcule a l'instant present ;
 * les champs `reward*` qui suivent exposent la formule (base + bonus participants + bonus
 * volume, plafonne) pour l'expliquer a l'ecran — aucune valeur n'est recalculee cote client.
 */
export interface Pool {
  id: string;
  code: string;
  creatorId: string;
  currencyPair: string;
  targetAmountXof: string;
  currentAmountXof: string;
  status: PoolStatus;
  participantCount: number;
  rewardMarginReductionPercentage: string;
  rewardBasePercentage: string;
  /** Points venant du NOMBRE de participants : perParticipant * (participants - 1). */
  rewardParticipantBonusPercentage: string;
  /** Points venant du VOLUME echange par le groupe : perMillion * floor(volume / 1M). */
  rewardVolumeBonusPercentage: string;
  rewardPerParticipantPercentage: string;
  rewardPerMillionXofPercentage: string;
  rewardMaxPercentage: string;
  createdAt: string;
  expiresAt: string;
  succeededAt: string | null;
  expiredAt: string | null;
  cancelledAt: string | null;
  viewerIsParticipant: boolean;
  viewerIsCreator: boolean;
}

/** `firstName` uniquement (jamais le nom complet ni le telephone d'un tiers). */
export interface PoolParticipant {
  userId: string;
  firstName: string;
  isCreator: boolean;
  joinedAt: string;
  contributedAmountXof: string;
}

/** `durationMinutes` : 5 a 180 (verifie cote backend). */
export interface CreatePoolRequest {
  targetAmountXof: string;
  durationMinutes: number;
}

export function poolProgress(pool: Pool): number {
  const target = Number(pool.targetAmountXof);
  const current = Number(pool.currentAmountXof);
  if (!(target > 0)) return 0;
  return Math.min(Math.max(current / target, 0), 1);
}

export function poolIsActive(pool: Pool): boolean {
  return pool.status === 'ACTIVE';
}
