import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * Reserve les routes /admin au role ADMIN. S'appuie uniquement sur le
 * role renvoye par le backend a la connexion : le frontend ne decide
 * jamais qui est administrateur, il se contente de ne pas afficher les
 * ecrans correspondants a qui n'en a pas le droit — le backend
 * refuserait de toute facon chaque appel API (403).
 */
export const adminGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated() && authService.isAdmin()) {
    return true;
  }
  return router.createUrlTree(authService.isAuthenticated() ? ['/dashboard'] : ['/login']);
};
