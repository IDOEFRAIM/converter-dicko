import { Injectable } from '@angular/core';
import { OrderDetail, OrderStatus } from '../models/order.model';

export type SettlementStage = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED';

export interface SettlementView {
  stage: SettlementStage;
  label: string;
  description: string;
}

/**
 * Suivi du reglement CNY, cote client.
 *
 * <p>Le backend ne pas expose de {@code GET} de reglement au client
 * (l'entite {@code Settlement} est reservee a {@code /api/admin/**}) —
 * voir docs/ARCHITECTURE.md, Partie I, section H.3/P : le detail du
 * reglement (methode, reference operateur...) est une donnee
 * administrative. Le client suit neanmoins l'execution en Chine via le
 * statut deja public de son propre ordre : {@code PROCESSING} signifie
 * que le reglement est en cours, {@code COMPLETED} qu'il a ete execute.
 * Ce service se contente de deriver un affichage lisible de ce statut
 * — aucun appel reseau, aucune logique de reglement recreee ici.
 */
@Injectable({ providedIn: 'root' })
export class SettlementService {
  deriveFromOrder(order: OrderDetail): SettlementView {
    return this.deriveFromStatus(order.status);
  }

  deriveFromStatus(status: OrderStatus): SettlementView {
    switch (status) {
      case 'PROCESSING':
        return {
          stage: 'IN_PROGRESS',
          label: 'Reglement en cours',
          description:
            "Votre paiement est confirme. L'equipe execute actuellement le versement en Chine.",
        };
      case 'COMPLETED':
        return {
          stage: 'COMPLETED',
          label: 'Reglement execute',
          description: 'Le versement au beneficiaire en Chine a ete effectue.',
        };
      default:
        return {
          stage: 'NOT_STARTED',
          label: 'Pas encore commence',
          description: 'Le reglement demarrera une fois votre paiement verifie.',
        };
    }
  }
}
