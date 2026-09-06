import 'order_models.dart';

/// Titre + description affiches sous le statut d'un ordre — texte purement
/// derive de [OrderStatus], jamais un etat invente. Miroir exact de
/// `ORDER_STATUS_MESSAGES` cote Angular (`order.model.ts`).
class OrderStatusMessage {
  final String title;
  final String description;

  const OrderStatusMessage(this.title, this.description);
}

const Map<OrderStatus, OrderStatusMessage> orderStatusMessages = {
  OrderStatus.awaitingPayment: OrderStatusMessage(
    'En attente de paiement',
    'Envoyez le montant indique puis declarez votre paiement.',
  ),
  OrderStatus.paymentSubmitted: OrderStatusMessage(
    'Paiement en verification',
    'Votre paiement a ete declare. Notre equipe le verifie.',
  ),
  OrderStatus.paymentVerified: OrderStatusMessage(
    'Paiement verifie',
    'Votre paiement est confirme. Le reglement en Chine va demarrer.',
  ),
  OrderStatus.processing: OrderStatusMessage(
    'En traitement',
    'Votre paiement a ete verifie. Le reglement en Chine est en cours.',
  ),
  OrderStatus.completed: OrderStatusMessage('Transfert termine', 'Le fournisseur a ete paye en Chine.'),
  OrderStatus.cancelled: OrderStatusMessage('Annule', 'Cet ordre a ete annule.'),
  OrderStatus.rejected: OrderStatusMessage('Paiement rejete', 'Le paiement declare pour cet ordre a ete rejete.'),
  OrderStatus.expired: OrderStatusMessage('Expire', 'Le delai de paiement a ete depasse.'),
  OrderStatus.unknown: OrderStatusMessage('Statut inconnu', ''),
};

enum SettlementStage { notStarted, inProgress, completed }

class SettlementView {
  final SettlementStage stage;
  final String label;
  final String description;

  const SettlementView(this.stage, this.label, this.description);
}

/// Miroir exact de `SettlementService.deriveFromStatus` cote Angular : le
/// mobile (client final) ne consulte JAMAIS `/api/admin/settlements/*` (reserve
/// aux administrateurs) — le suivi du reglement se deduit uniquement du
/// statut de l'ordre lui-meme (mission section 4 du document de reference API).
SettlementView deriveSettlementView(OrderStatus status) {
  switch (status) {
    case OrderStatus.processing:
      return const SettlementView(
        SettlementStage.inProgress,
        'Reglement en cours',
        'Votre paiement est confirme. L\'equipe execute actuellement le versement en Chine.',
      );
    case OrderStatus.completed:
      return const SettlementView(
        SettlementStage.completed,
        'Reglement execute',
        'Le versement au beneficiaire en Chine a ete effectue.',
      );
    default:
      return const SettlementView(
        SettlementStage.notStarted,
        'Pas encore commence',
        'Le reglement demarrera une fois votre paiement verifie.',
      );
  }
}
