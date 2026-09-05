-- =====================================================================
-- V22 — Lien tracable Order -> Supplier (Phase 2, evolution Burkina Faso
-- <-> Chine)
--
-- Nullable, jamais lu pour reconstruire ou recalculer un beneficiaire
-- historique : `orders.supplier_id` repond uniquement a "quel
-- fournisseur enregistre a ete utilise pour cette transaction ?". Le
-- snapshot financier immuable reste entierement porte par
-- `beneficiaries` (inchangee par cette migration). Aucune cascade de
-- suppression : un fournisseur desactive (jamais supprime, voir V21)
-- reste referencable par l'historique des ordres qui l'ont utilise.
-- =====================================================================

ALTER TABLE orders ADD COLUMN supplier_id UUID NULL;

ALTER TABLE orders
    ADD CONSTRAINT fk_orders_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id);

CREATE INDEX idx_orders_supplier ON orders (supplier_id);
