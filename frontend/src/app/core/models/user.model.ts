export type RoleCode = 'USER' | 'ADMIN';

/**
 * Habillage choisi par le client (miroir de `ExperienceProfile` cote backend et mobile).
 * Pilote UNIQUEMENT les couleurs et le ton de l'interface — jamais le taux ni les frais.
 */
export type ExperienceProfile = 'PRO' | 'STUDENT_MALE' | 'STUDENT_FEMALE';

export interface CurrentUser {
  id: string;
  phone: string;
  firstName: string;
  lastName: string;
  email: string | null;
  status: 'ACTIVE' | 'BLOCKED';
  roles: string[];
  createdAt: string;
  lastLoginAt: string | null;
  /** Absent sur un backend plus ancien : traite comme `PRO`. */
  experienceProfile?: ExperienceProfile | null;
  /** Identite verifiee (KYC) — requis au-dela d'un seuil de montant. */
  kycVerified?: boolean;
  /** Faux pour un compte cree via Google (pas de mot de passe). */
  hasPassword?: boolean;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
  user: CurrentUser;
}

export interface RegisterRequest {
  phone: string;
  password: string;
  firstName: string;
  lastName: string;
}

export interface LoginRequest {
  phone: string;
  password: string;
}
