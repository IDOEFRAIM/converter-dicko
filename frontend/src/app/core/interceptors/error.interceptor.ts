import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

/**
 * Reaction globale aux erreurs HTTP de l'API.
 *
 * <p>401 : le jeton est absent/expire/invalide — la session locale est
 * purgee et l'utilisateur est renvoye vers /login. Les autres erreurs
 * (404, 409, 400...) sont laissees telles quelles : chaque ecran les
 * traite avec un message contextuel via {@code extractErrorMessage}.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const authService = inject(AuthService);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && req.url.startsWith('/api')) {
        const wasAuthenticated = authService.isAuthenticated();
        authService.logout();
        if (wasAuthenticated) {
          router.navigate(['/login'], { queryParams: { sessionExpired: true } });
        }
      }
      return throwError(() => error);
    }),
  );
};
