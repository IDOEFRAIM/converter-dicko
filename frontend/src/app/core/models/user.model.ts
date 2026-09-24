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
