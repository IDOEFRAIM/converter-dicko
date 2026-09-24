import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  isDevMode,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideServiceWorker } from '@angular/service-worker';
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
    // PWA (mission "blocages Apple/Meta" oct. 2026) : jamais actif en dev (isDevMode), et
    // jamais avant que l'app soit stable -- un enregistrement immediat entrerait en concurrence
    // avec le chargement initial. Aucun `dataGroups` dans ngsw-config.json : les reponses de
    // l'API (donnees financieres) ne sont JAMAIS mises en cache par le service worker, seule la
    // coquille applicative (JS/CSS/icones) l'est -- une donnee perimee ne doit jamais paraitre
    // fraiche sur cette application. Fichier `push-worker.js` (pas `ngsw-worker.js` directement) :
    // il importe ngsw-worker.js ET ajoute la reception des notifications Web Push, qu'Angular ne
    // gere pas nativement (voir push-worker.js).
    provideServiceWorker('push-worker.js', {
      enabled: !isDevMode(),
      // Enregistrement quasi immediat : l'application n'est JAMAIS "stable" (sondage des
      // notifications toutes les 30 s), 'registerWhenStable:30000' retardait donc toujours le
      // service worker de 30 s -- et avec lui la proposition d'installation de Chrome.
      registrationStrategy: 'registerWithDelay:2000',
    }),
  ],
};
