/** Miroir de `com.converter.kyc.domain` + `KycAdminSubmissionResponse` (remarque produit #6). */

export type KycDocumentType = 'NATIONAL_ID' | 'PASSPORT' | 'RESIDENCE_PERMIT';

export type KycSubmissionStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

/** Type de fichier recuperable via `GET /admin/kyc/submissions/{id}/files/{kind}`. */
export type KycFileKind = 'front' | 'back' | 'selfie';

export interface KycAdminSubmission {
  id: string;
  userId: string;
  userFullName: string;
  userPhone: string;
  status: KycSubmissionStatus;
  documentType: KycDocumentType;
  hasBack: boolean;
  submittedAt: string;
}

export interface RejectKycRequest {
  reason: string;
}

/** Vue du client sur son propre dossier — miroir de `KycSubmissionResponse` (`GET /v1/kyc/submissions/me`). */
export interface KycSubmission {
  status: KycSubmissionStatus;
  documentType: KycDocumentType;
  submittedAt: string;
  reviewedAt: string | null;
  rejectionReason: string | null;
}

/** Le passeport n'a pas de verso pertinent — meme regle que `KycDocumentType.needsBack` (mobile). */
export function kycDocumentNeedsBack(type: KycDocumentType): boolean {
  return type !== 'PASSPORT';
}

export const KYC_DOCUMENT_TYPE_LABELS: Record<KycDocumentType, string> = {
  NATIONAL_ID: "Carte nationale d'identite (CNIB)",
  PASSPORT: 'Passeport',
  RESIDENCE_PERMIT: 'Carte de sejour',
};
