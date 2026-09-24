import { Injectable } from '@angular/core';

const TOKEN_KEY = 'converter.access_token';

/**
 * Point d'acces unique au jeton JWT persiste.
 *
 * <p>Seul le jeton est conserve — jamais le mot de passe, jamais de
 * donnee sensible additionnelle. Le localStorage reste accessible a
 * tout script s'executant sur la page (risque XSS classique d'un SPA) ;
 * c'est un compromis MVP standard, attenue par une duree de vie courte
 * du jeton (2 h, cote backend) et par le retrait immediat au logout.
 */
@Injectable({ providedIn: 'root' })
export class TokenStorageService {
  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  setToken(token: string): void {
    localStorage.setItem(TOKEN_KEY, token);
  }

  clear(): void {
    localStorage.removeItem(TOKEN_KEY);
  }
}
