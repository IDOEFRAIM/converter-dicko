import { HttpHeaders } from '@angular/common/http';

/**
 * Cle d'idempotence pour UNE tentative utilisateur d'operation mutante
 * (creation d'ordre, soumission de paiement, "payer a nouveau").
 *
 * <p>Le backend reste la seule autorite sur l'idempotence (table
 * {@code idempotency_keys}, en-tete {@code Idempotency-Key}). Cote client, la regle
 * appliquee est portee par {@link IdempotencyAttempt} :
 * <ul>
 *   <li>une cle est generee au premier envoi ;</li>
 *   <li>la MEME cle est conservee tant que l'utilisateur rejoue <b>exactement la meme
 *       requete</b> apres un echec (erreur reseau, timeout, nouveau clic) — le backend peut
 *       alors rejouer/dedupliquer sans risque de doublon ;</li>
 *   <li>si l'utilisateur <b>modifie</b> la requete (montant, beneficiaire, reference...), c'est
 *       une nouvelle intention : une nouvelle cle est generee, sinon le backend renverrait
 *       {@code 409 IDEMPOTENCY_KEY_REUSED} et bloquerait une operation legitime ;</li>
 *   <li>apres un succes, la cle est oubliee.</li>
 * </ul>
 */
export function newIdempotencyKey(): string {
  return crypto.randomUUID();
}

/** En-tetes HTTP portant la cle d'idempotence, ou en-tetes vides si aucune cle n'est fournie. */
export function idempotencyHeaders(key: string | null | undefined): HttpHeaders | undefined {
  return key ? new HttpHeaders({ 'Idempotency-Key': key }) : undefined;
}

/**
 * Empreinte stable d'un corps de requete, insensible a l'ordre des cles. Deux corps
 * equivalents produisent la meme empreinte meme si leurs proprietes sont ordonnees
 * differemment.
 */
function fingerprint(payload: unknown): string {
  const seen = new WeakSet<object>();
  const normalise = (value: unknown): unknown => {
    if (value === null || typeof value !== 'object') {
      return value;
    }
    if (seen.has(value as object)) {
      return null;
    }
    seen.add(value as object);
    if (Array.isArray(value)) {
      return value.map(normalise);
    }
    return Object.keys(value as Record<string, unknown>)
      .sort()
      .reduce<Record<string, unknown>>((acc, key) => {
        acc[key] = normalise((value as Record<string, unknown>)[key]);
        return acc;
      }, {});
  };
  return JSON.stringify(normalise(payload));
}

/**
 * Suivi d'une tentative d'operation idempotente cote composant. Une instance par action
 * (ex. un champ prive de la page de creation d'ordre).
 */
export class IdempotencyAttempt {
  private key: string | null = null;
  private lastFingerprint: string | null = null;

  /**
   * Cle a envoyer pour cet envoi. Nouvelle cle si aucune tentative n'est en cours ou si
   * {@code payload} differe de la derniere tentative ; sinon la cle precedente est reutilisee.
   */
  keyFor(payload: unknown): string {
    const current = fingerprint(payload);
    if (this.key === null || current !== this.lastFingerprint) {
      this.key = newIdempotencyKey();
      this.lastFingerprint = current;
    }
    return this.key;
  }

  /** A appeler apres un succes : le prochain envoi est une nouvelle intention. */
  complete(): void {
    this.key = null;
    this.lastFingerprint = null;
  }
}
