/** Comptes clients, cote administration — aligne sur `com.converter.user.dto`. */

export type UserAccountStatus = 'ACTIVE' | 'BLOCKED';

/** Ligne de la liste des comptes. */
export interface AdminUserSummary {
  id: string;
  phone: string;
  fullName: string;
  status: UserAccountStatus;
  createdAt: string;
  kycVerified: boolean;
  orderCount: number;
  totalAmountCfa: string;
}

/** Fiche complete d'un compte. */
export interface AdminUserDetail {
  id: string;
  phone: string;
  firstName: string;
  lastName: string;
  email: string | null;
  status: UserAccountStatus;
  roles: string[];
  createdAt: string;
  lastLoginAt: string | null;
  blockedAt: string | null;
  blockedReason: string | null;
  kycVerified: boolean;
  kycVerifiedAt: string | null;
  orderCount: number;
  totalAmountCfa: string;
}

export interface BlockUserRequest {
  reason: string;
}
