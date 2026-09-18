-- =====================================================================
-- V42 — supprime payments.transaction_reference (retour client oct. 2026 :
-- "on lui demande une reference, c'est un doublon inutile vu qu'on va
-- envoyer apres une photo comme preuve").
--
-- Decision produit assumee : la preuve photo devient la SEULE piece
-- justificative demandee au client pour declarer un paiement. Ceci retire
-- la protection anti-doublon documentee dans docs/FINANCIAL_INVARIANTS.md
-- (risque J.1 #6 : reutilisation d'une reference Mobile Money sur deux
-- ordres, mitigee par `uq_payments_txref`) -- desormais a la seule charge
-- de la revue manuelle des preuves photo par l'administration.
-- =====================================================================

DROP INDEX IF EXISTS uq_payments_txref;

ALTER TABLE payments
    DROP COLUMN IF EXISTS transaction_reference;
