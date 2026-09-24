-- =====================================================================
-- V23 — Motif du transfert sur Order (Phase 2, evolution Burkina Faso
-- <-> Chine)
--
-- Nullable : les ordres existants, crees avant l'introduction de ce
-- champ, restent valides sans regression (`ddl-auto=validate` n'exige
-- aucun backfill sur une colonne nullable). Meme catalogue que
-- `suppliers.purpose` (V21) — voir com.converter.supplier.domain.Purpose,
-- desormais reutilise par les deux tables.
-- =====================================================================

ALTER TABLE orders ADD COLUMN purpose VARCHAR(24) NULL;
ALTER TABLE orders ADD COLUMN purpose_details VARCHAR(500) NULL;

ALTER TABLE orders ADD CONSTRAINT ck_orders_purpose CHECK (purpose IS NULL OR purpose IN (
    'PERSONAL', 'EDUCATION', 'FAMILY_SUPPORT', 'IMPORT_GOODS', 'SERVICES', 'BUSINESS', 'OTHER'));
