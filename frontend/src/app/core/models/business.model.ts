/**
 * Profil professionnel + reporting consolide. Aligne sur `com.converter.business`.
 * Le statut PERSONAL/BUSINESS n'est PAS un champ : il decoule uniquement de l'existence
 * d'un profil (404 sur `GET /business-profile` => l'utilisateur est PERSONAL).
 * Aucun recalcul financier cote client : le reporting affiche les valeurs backend telles quelles.
 */

export type BusinessType = 'IMPORTER' | 'MERCHANT' | 'SERVICES' | 'OTHER';

export interface BusinessProfile {
  id: string;
  businessName: string;
  businessType: BusinessType;
  registrationNumber: string | null;
  country: string;
  city: string | null;
  address: string | null;
  createdAt: string;
  updatedAt: string;
}

/** Corps de `PUT /api/v1/business-profile` — jamais de `userId` (le proprietaire = l'utilisateur authentifie). */
export interface UpsertBusinessProfileRequest {
  businessName: string;
  businessType: BusinessType;
  registrationNumber: string | null;
  country: string;
  city: string | null;
  address: string | null;
}

export interface ReportPeriod {
  from: string | null;
  to: string | null;
}

export interface BusinessPaymentSummary {
  period: ReportPeriod;
  transferCount: number;
  completedCount: number;
  cancelledCount: number;
  rejectedCount: number;
  totalAmountXof: string;
  totalAmountCny: string;
  totalFeesXof: string;
}

export const BUSINESS_TYPE_LABELS: Record<BusinessType, string> = {
  IMPORTER: 'Importateur',
  MERCHANT: 'Commercant',
  SERVICES: 'Services',
  OTHER: 'Autre',
};

export const BUSINESS_TYPE_OPTIONS: { value: BusinessType; label: string }[] = (
  Object.keys(BUSINESS_TYPE_LABELS) as BusinessType[]
).map((value) => ({ value, label: BUSINESS_TYPE_LABELS[value] }));
