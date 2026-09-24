import { HttpClient } from '@angular/common/http';
import { Injectable, computed, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models/api-response.model';
import {
  AuthResponse,
  CurrentUser,
  ExperienceProfile,
  LoginRequest,
  RegisterRequest,
} from '../models/user.model';
import { TokenStorageService } from './token-storage.service';

/**
 * Authentification et session courante.
 *
 * <p>La verite sur "qui est l'utilisateur et quel role a-t-il" vient
 * exclusivement du backend (champ {@code roles} de la reponse de
 * connexion, ou de {@code GET /api/auth/me}) — jamais decode ni deduit
 * cote client a partir du contenu du jeton.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly baseUrl = `${environment.apiBaseUrl}/auth`;

  private readonly currentUserSignal = signal<CurrentUser | null>(null);
  private readonly initializedSignal = signal(false);

  readonly currentUser = this.currentUserSignal.asReadonly();
  readonly initialized = this.initializedSignal.asReadonly();
  readonly isAuthenticated = computed(() => this.currentUserSignal() !== null);
  readonly isAdmin = computed(() => (this.currentUserSignal()?.roles ?? []).includes('ADMIN'));
  /** Habillage courant (PRO par defaut, comme `AuthSession.experienceProfile` cote mobile). */
  readonly experienceProfile = computed<ExperienceProfile>(
    () => this.currentUserSignal()?.experienceProfile ?? 'PRO',
  );

  constructor(
    private readonly http: HttpClient,
    private readonly tokenStorage: TokenStorageService,
  ) {}

  login(request: LoginRequest): Observable<ApiResponse<AuthResponse>> {
    return this.http.post<ApiResponse<AuthResponse>>(`${this.baseUrl}/login`, request).pipe(
      tap((response) => {
        this.tokenStorage.setToken(response.data.accessToken);
        this.currentUserSignal.set(response.data.user);
      }),
    );
  }

  register(request: RegisterRequest): Observable<ApiResponse<unknown>> {
    return this.http.post<ApiResponse<unknown>>(`${this.baseUrl}/register`, request);
  }

  /** Recharge la session a partir du jeton persiste (rechargement de page). */
  restoreSession(): Observable<ApiResponse<CurrentUser>> {
    return this.http.get<ApiResponse<CurrentUser>>(`${this.baseUrl}/me`).pipe(
      tap({
        next: (response) => {
          this.currentUserSignal.set(response.data);
          this.initializedSignal.set(true);
        },
        error: () => {
          this.tokenStorage.clear();
          this.currentUserSignal.set(null);
          this.initializedSignal.set(true);
        },
      }),
    );
  }

  /** Change l'habillage de l'interface (meme endpoint que le selecteur de profil mobile). */
  updateExperienceProfile(experienceProfile: ExperienceProfile): Observable<ApiResponse<CurrentUser>> {
    return this.http
      .patch<ApiResponse<CurrentUser>>(`${this.baseUrl}/me/experience-profile`, { experienceProfile })
      .pipe(tap((response) => this.currentUserSignal.set(response.data)));
  }

  hasToken(): boolean {
    return this.tokenStorage.getToken() !== null;
  }

  markInitialized(): void {
    this.initializedSignal.set(true);
  }

  logout(): void {
    this.tokenStorage.clear();
    this.currentUserSignal.set(null);
  }

  /** {@code currentPassword} obligatoire sauf pour un compte cree via Google (voir CurrentUser.hasPassword). */
  deleteAccount(currentPassword?: string | null): Observable<ApiResponse<void>> {
    return this.http
      .delete<ApiResponse<void>>(`${this.baseUrl}/me`, {
        body: currentPassword ? { currentPassword } : {},
      })
      .pipe(tap(() => this.logout()));
  }
}
