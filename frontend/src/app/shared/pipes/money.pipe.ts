import { Pipe, PipeTransform } from '@angular/core';

/**
 * Formate un montant renvoye par le backend (chaine decimale) pour
 * l'affichage — jamais pour un calcul. XOF n'a pas de sous-unite,
 * CNY en a deux.
 */
@Pipe({ name: 'money', standalone: true })
export class MoneyPipe implements PipeTransform {
  transform(value: string | number | null | undefined, currency: 'XOF' | 'CNY'): string {
    if (value === null || value === undefined || value === '') {
      return '—';
    }
    const amount = typeof value === 'string' ? Number(value) : value;
    if (Number.isNaN(amount)) {
      return '—';
    }
    const formatted = new Intl.NumberFormat('fr-FR', {
      minimumFractionDigits: currency === 'CNY' ? 2 : 0,
      maximumFractionDigits: currency === 'CNY' ? 2 : 0,
    }).format(amount);
    return `${formatted} ${currency}`;
  }
}
