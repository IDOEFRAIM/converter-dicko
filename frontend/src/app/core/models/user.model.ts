export type RoleCode = 'USER' | 'ADMIN';

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
  /** Faux pour un compte cree via Google Sign-In (jamais de mot de passe) — pilote l'ecran de
   * suppression de compte : demander une re-confirmation par mot de passe uniquement si vrai. */
  hasPassword: boolean;
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
