import { HttpErrorResponse } from '@angular/common/http';
import { errorCode, extractErrorMessage } from './api-error.util';

describe('extractErrorMessage', () => {
  it('returns the backend message when the body follows ErrorResponse', () => {
    const error = new HttpErrorResponse({
      status: 409,
      error: { code: 'QUOTE_ALREADY_USED', message: 'Ce devis a deja ete utilise.' },
    });
    expect(extractErrorMessage(error)).toBe('Ce devis a deja ete utilise.');
  });

  it('falls back to a generic message for a 404 without a body', () => {
    const error = new HttpErrorResponse({ status: 404 });
    expect(extractErrorMessage(error)).toBe('Ressource introuvable ou inaccessible.');
  });

  it('falls back to a network message for status 0', () => {
    const error = new HttpErrorResponse({ status: 0 });
    expect(extractErrorMessage(error)).toContain('Connexion au serveur');
  });

  it('falls back to a generic server message for a 5xx', () => {
    const error = new HttpErrorResponse({ status: 500 });
    expect(extractErrorMessage(error)).toContain('erreur interne');
  });

  it('falls back to a generic message for a non-HTTP error', () => {
    expect(extractErrorMessage(new Error('boom'))).toBe('Une erreur inattendue est survenue.');
  });
});

describe('errorCode', () => {
  it('extracts the stable machine-readable code', () => {
    const error = new HttpErrorResponse({ status: 409, error: { code: 'INVALID_ORDER_STATE' } });
    expect(errorCode(error)).toBe('INVALID_ORDER_STATE');
  });

  it('returns null when unavailable', () => {
    expect(errorCode(new Error('boom'))).toBeNull();
  });
});
