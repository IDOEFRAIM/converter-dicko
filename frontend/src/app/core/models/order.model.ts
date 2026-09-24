import { Purpose } from './common.model';

export type OrderStatus =
  | 'AWAITING_PAYMENT'
  | 'PAYMENT_SUBMITTED'
  | 'PAYMENT_VERIFIED'
  | 'PROCESSING'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'REJECTED'
  | 'EXPIRED';

/**
 * Titre + description affiches sous le statut d'un ordre (Order Detail) — texte purement
 * derive de `OrderStatus`, jamais un etat invente. Aucune donnee financiere/reference n'est
 * fabriquee ici : uniquement du texte d'accompagnement pour un statut deja reel.
 */
export const ORDER_STATUS_MESSAGES: Record<OrderStatus, { title: string; description: string }> = {
  AWAITING_PAYMENT: {
    title: 'En attente de paiement',
    description: 'Envoyez le montant indique puis declarez votre paiement.',
  },
  PAYMENT_SUBMITTED: {
    title: 'Paiement en verification',
    description: 'Votre paiement a ete declare. Notre equipe le verifie.',
  },
  PAYMENT_VERIFIED: {
    title: 'Paiement verifie',
    description: 'Votre paiement est confirme. Le reglement en Chine va demarrer.',
  },
  PROCESSING: {
    title: 'En traitement',
    description: 'Votre paiement a ete verifie. Le reglement en Chine est en cours.',
  },
  COMPLETED: {
    title: 'Transfert termine',
    description: 'Le fournisseur a ete paye en Chine.',
  },
  CANCELLED: {
    title: 'Annule',
    description: 'Cet ordre a ete annule.',
  },
  REJECTED: {
    title: 'Paiement rejete',
    description: 'Le paiement declare pour cet ordre a ete rejete.',
  },
  EXPIRED: {
    title: 'Expire',
    description: 'Le delai de paiement a ete depasse.',
  },
};

export type BeneficiaryType = 'ALIPAY' | 'WECHAT_PAY' | 'CHINESE_BANK_ACCOUNT';

export const BENEFICIARY_TYPE_LABELS: Record<BeneficiaryType, string> = {
  ALIPAY: 'Alipay',
  WECHAT_PAY: 'WeChat Pay',
  CHINESE_BANK_ACCOUNT: 'Compte bancaire chinois',
};

export const BENEFICIARY_TYPE_OPTIONS: { value: BeneficiaryType; label: string }[] = (
  Object.keys(BENEFICIARY_TYPE_LABELS) as BeneficiaryType[]
).map((value) => ({ value, label: BENEFICIARY_TYPE_LABELS[value] }));

/**
 * Libelle du champ "identifiant" — sa nature reelle differe par type (retour
 * client) : Alipay/WeChat Pay s'utilisent en Chine via un code QR, jamais un
 * identifiant de compte classique comme une banque.
 */
export const BENEFICIARY_IDENTIFIER_LABELS: Record<BeneficiaryType, string> = {
  ALIPAY: 'Code QR Alipay',
  WECHAT_PAY: 'Code QR WeChat Pay',
  CHINESE_BANK_ACCOUNT: 'Numero de compte bancaire',
};

/**
 * Precision affichee sous le champ — saisie manuelle ponctuelle (sans fournisseur enregistre),
 * ce formulaire reste un champ texte : aucun televersement de code QR ici. Pour joindre le vrai
 * code QR (une image, jamais un texte), voir le carnet de fournisseurs (`SupplierFormPage`).
 */
export const BENEFICIARY_IDENTIFIER_HINTS: Partial<Record<BeneficiaryType, string>> = {
  ALIPAY: 'Alias, numero de telephone ou identifiant associe a ce compte.',
  WECHAT_PAY: 'Alias, numero de telephone ou identifiant associe a ce compte.',
};

export interface Beneficiary {
  type: BeneficiaryType;
  fullName: string;
  identifier: string;
  bankName: string | null;
  bankBranch: string | null;
}

export interface BeneficiaryRequest {
  type: BeneficiaryType;
  fullName: string;
  identifier: string;
  bankName: string | null;
  bankBranch: string | null;
}

export interface CreateOrderRequest {
  quoteId: string;
  /** Exactement l'un de `beneficiary` ou `supplierId` (verifie cote backend). */
  beneficiary: BeneficiaryRequest | null;
  note: string | null;
  /** Fournisseur deja enregistre a utiliser — omis si `beneficiary` est fourni. */
  supplierId?: string | null;
  purpose?: Purpose | null;
  purposeDetails?: string | null;
  /** Ruee collective a laquelle cet ordre contribue, optionnelle — le client doit deja l'avoir
   * rejointe (voir PoolService.join). */
  poolId?: string | null;
}

export interface CancelOrderRequest {
  reason: string;
}

export interface OrderStatusHistoryEntry {
  fromStatus: OrderStatus | null;
  toStatus: OrderStatus;
  changedBy: string | null;
  reason: string | null;
  createdAt: string;
}

export interface OrderSummary {
  id: string;
  reference: string;
  status: OrderStatus;
  amountXof: string;
  amountCny: string;
  createdAt: string;
}

/** Ligne de l'historique enrichi (`GET /api/v1/orders/history`). */
export interface OrderHistoryEntry {
  id: string;
  reference: string;
  status: OrderStatus;
  amountXof: string;
  amountCny: string;
  feeXof: string;
  customerRate: string;
  purpose: Purpose | null;
  supplierId: string | null;
  createdAt: string;
  completedAt: string | null;
}

/**
 * Faisabilité d'un ordre à partir d'un devis accepté, à vérifier AVANT la saisie du
 * bénéficiaire. N'expose aucun solde de trésorerie — juste un booléen et le montant CNY déjà
 * visible sur le devis. Indicateur d'affichage : la vérité reste la réservation à la création.
 */
export interface OrderFeasibility {
  quoteId: string;
  amountCny: string;
  settlementReservationEnabled: boolean;
  sufficientLiquidity: boolean;
}

/** Filtres optionnels de l'historique enrichi — tous cotes serveur (jamais un filtrage local). */
export interface OrderHistoryQuery {
  status?: OrderStatus | null;
  purpose?: Purpose | null;
  supplierId?: string | null;
  /** Bornes ISO-8601 : `from <= createdAt < to`. */
  from?: string | null;
  to?: string | null;
  page?: number;
  size?: number;
}

export interface OrderDetail {
  id: string;
  reference: string;
  quoteId: string;
  status: OrderStatus;
  amountXof: string;
  amountCny: string;
  customerRate: string;
  feeXof: string;
  netAmountXof: string;
  note: string | null;
  cancellationReason: string | null;
  rejectionReason: string | null;
  beneficiary: Beneficiary;
  statusHistory: OrderStatusHistoryEntry[];
  createdAt: string;
  updatedAt: string;
  /** Echeance de paiement ; au-dela, l'ordre est expire et sa reservation liberee. */
  paymentDeadlineAt: string;
  completedAt: string | null;
  cancelledAt: string | null;
  /** Fournisseur enregistre utilise, purement tracable — null si beneficiaire saisi directement. */
  supplierId: string | null;
  purpose: Purpose | null;
  purposeDetails: string | null;
  /** Facture proforma telechargeable (parcours "payer un fournisseur"). */
  proformaAvailable?: boolean;
}
