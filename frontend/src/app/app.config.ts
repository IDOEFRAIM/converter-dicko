import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideRouter } from '@angular/router';
import { catchError, of } from 'rxjs';
import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { errorInterceptor } from './core/interceptors/error.interceptor';
import { AuthService } from './core/services/auth.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
    provideAnimationsAsync(),
    // Recharge la session (utilisateur + roles) a partir du jeton
    // persiste AVANT le premier rendu, pour que les guards voient un
    // etat d'authentification correct des la navigation initiale
    // (rechargement de page, ouverture d'un lien direct).
    provideAppInitializer(() => {
      const authService = inject(AuthService);
      if (!authService.hasToken()) {
        authService.markInitialized();
        return of(null);
      }
      return authService.restoreSession().pipe(catchError(() => of(null)));
    }),
  ],
};
