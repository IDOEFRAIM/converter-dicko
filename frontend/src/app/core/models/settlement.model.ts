export type SettlementStatus = 'PENDING' | 'EXECUTED';

export interface SettlementProof {
  id: string;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  uploadedAt: string;
}

/**
 * Reglement CNY — expose uniquement cote ADMIN par le backend. Le
 * client suit l'execution en Chine indirectement, via le statut de
 * son Order (PROCESSING/COMPLETED) : voir shared/components pour le
 * composant qui derive un affichage "Reglement" du statut de l'ordre,
 * sans jamais appeler un endpoint reserve a l'administration.
 */
export interface Settlement {
  id: string;
  orderId: string;
  status: SettlementStatus;
  amountCny: string;
  method: string;
  beneficiaryFullName: string;
  beneficiaryIdentifier: string;
  beneficiaryBankName: string | null;
  beneficiaryBankBranch: string | null;
  settlementReference: string | null;
  notes: string | null;
  executedBy: string | null;
  proofs: SettlementProof[];
  createdAt: string;
  executedAt: string | null;
}

export interface ExecuteSettlementRequest {
  settlementReference: string;
  notes: string | null;
}
