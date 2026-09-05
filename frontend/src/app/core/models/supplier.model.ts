import { BeneficiaryType } from './order.model';
import { Currency, Purpose } from './common.model';

/** Carnet de fournisseurs/beneficiaires reutilisables — aligne sur `com.converter.supplier`. */

export type SupplierStatus = 'ACTIVE' | 'INACTIVE';

/** Ligne de liste : le backend ne renvoie jamais le numero de compte en clair ici. */
export interface SupplierSummary {
  id: string;
  type: BeneficiaryType;
  displayName: string;
  country: string | null;
  city: string | null;
  /** Ex. `******1234` — seuls les 4 derniers caracteres sont visibles. */
  maskedAccountNumber: string;
  purpose: Purpose | null;
  favorite: boolean;
  status: SupplierStatus;
  createdAt: string;
}

/** Detail complet — numero de compte en clair, reserve a une consultation deliberee du proprietaire. */
export interface SupplierDetail {
  id: string;
  type: BeneficiaryType;
  displayName: string;
  legalName: string | null;
  phone: string | null;
  email: string | null;
  country: string | null;
  city: string | null;
  province: string | null;
  bankName: string | null;
  bankBranch: string | null;
  accountName: string | null;
  accountNumber: string;
  bankAddress: string | null;
  swiftCode: string | null;
  currency: Currency;
  purpose: Purpose | null;
  notes: string | null;
  favorite: boolean;
  status: SupplierStatus;
  createdAt: string;
  updatedAt: string;
}

/** Corps de `POST` / `PUT /api/v1/suppliers` — meme forme pour la creation et la mise a jour. */
export interface SupplierRequest {
  type: BeneficiaryType;
  displayName: string;
  legalName: string | null;
  phone: string | null;
  email: string | null;
  country: string | null;
  city: string | null;
  province: string | null;
  bankName: string | null;
  bankBranch: string | null;
  accountName: string | null;
  accountNumber: string;
  bankAddress: string | null;
  swiftCode: string | null;
  currency: Currency;
  purpose: Purpose | null;
  notes: string | null;
}

/**
 * Corps de `POST /api/v1/suppliers/{id}/pay-again`. Le montant est TOUJOURS saisi par
 * l'utilisateur : jamais repris d'un ordre precedent (le backend cree un nouveau devis au
 * pricing courant, puis un nouvel ordre).
 */
export interface PayAgainRequest {
  amountXof: string;
  purpose: Purpose | null;
  purposeDetails: string | null;
}
