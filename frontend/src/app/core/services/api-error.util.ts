import { HttpErrorResponse } from '@angular/common/http';
import { ErrorResponse } from '../models/api-response.model';

/**
 * Extrait le message destine a l'humain d'une erreur backend, avec un
 * message de repli si le corps ne suit pas le format {@link ErrorResponse}
 * attendu (panne reseau, backend injoignable, etc.).
 *
 * <p>Une ressource appartenant a un autre utilisateur revient toujours
 * en 404 (jamais 403) — voir docs/ARCHITECTURE.md. Ce message generique
 * couvre ce cas sans jamais suggerer que la ressource existe.
 */
export function extractErrorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const body = error.error as ErrorResponse | undefined;
    if (body?.message) {
      return body.message;
    }
    if (error.status === 404) {
      return 'Ressource introuvable ou inaccessible.';
    }
    if (error.status === 0) {
      return 'Connexion au serveur impossible. Verifiez votre reseau.';
    }
    if (error.status >= 500) {
      return 'Une erreur interne est survenue. Reessayez dans quelques instants.';
    }
  }
  return 'Une erreur inattendue est survenue.';
}

export function errorCode(error: unknown): string | null {
  if (error instanceof HttpErrorResponse) {
    const body = error.error as ErrorResponse | undefined;
    return body?.code ?? null;
  }
  return null;
}
