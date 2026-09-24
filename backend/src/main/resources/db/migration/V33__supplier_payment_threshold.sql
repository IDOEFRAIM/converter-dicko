-- =====================================================================
-- V33 — Seuil "paiement fournisseur" (remarque produit #3)
--
-- Deux parcours partagent le meme moteur de change, mais au-dela de ce
-- montant un ordre est presente comme un PAIEMENT FOURNISSEUR : une
-- facture proforma est disponible (meme sans fournisseur enregistre) et
-- l'interface le signale. En dessous, c'est un simple echange personnel.
--
-- Purement une bascule d'affichage/service — le pricing (taux, marge,
-- frais) est identique de part et d'autre du seuil.
--
-- Public (is_public = TRUE) : le mobile s'en sert pour afficher la bonne
-- variante du parcours des la saisie du montant. La regle reste refaite
-- cote serveur (OrderProformaService).
-- =====================================================================

INSERT INTO system_settings (setting_key, value, value_type, description, is_public, updated_at) VALUES
    ('SUPPLIER_PAYMENT_THRESHOLD_XOF', '2000000', 'DECIMAL',
     'Montant XOF (inclus) a partir duquel un ordre est traite comme un paiement fournisseur '
        || '(facture proforma disponible). Ne change jamais le pricing.',
     TRUE, now())
ON CONFLICT (setting_key) DO NOTHING;
