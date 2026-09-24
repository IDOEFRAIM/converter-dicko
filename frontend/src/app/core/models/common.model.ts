/**
 * Types transverses au domaine, alignes sur les enums backend
 * (`com.converter.supplier.domain.Purpose`, `com.converter.treasury.domain.Currency`).
 * Le frontend ne fait que transporter ces valeurs — jamais de logique metier dessus.
 */

export type Purpose =
  | 'PERSONAL'
  | 'EDUCATION'
  | 'FAMILY_SUPPORT'
  | 'IMPORT_GOODS'
  | 'SERVICES'
  | 'BUSINESS'
  | 'OTHER';

export type Currency = 'XOF' | 'CNY';

export const PURPOSE_LABELS: Record<Purpose, string> = {
  PERSONAL: 'Personnel',
  EDUCATION: 'Études',
  FAMILY_SUPPORT: 'Soutien familial',
  IMPORT_GOODS: 'Import de marchandises',
  SERVICES: 'Services',
  BUSINESS: 'Professionnel',
  OTHER: 'Autre',
};

export const PURPOSE_OPTIONS: { value: Purpose; label: string }[] = (
  Object.keys(PURPOSE_LABELS) as Purpose[]
).map((value) => ({ value, label: PURPOSE_LABELS[value] }));
