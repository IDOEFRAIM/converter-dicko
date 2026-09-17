-- =====================================================================
-- V40 — suppression de compte en libre-service (retour client : "est-ce
-- que l'utilisateur a la possibilite de supprimer ses donnees ?").
--
-- Anonymisation, jamais un DELETE : les ordres/paiements/reglements d'un
-- utilisateur restent en base pour les obligations legales de
-- conservation (lutte anti-blanchiment, deja documentees dans la
-- politique de confidentialite publiee) — seul users.* est purge des
-- donnees personnelles (voir AccountDeletionService), jamais les tables
-- financieres qui le referencent par user_id.
-- =====================================================================

ALTER TABLE users DROP CONSTRAINT ck_users_status;
ALTER TABLE users ADD CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'BLOCKED', 'DELETED'));

ALTER TABLE users ADD COLUMN deleted_at TIMESTAMPTZ;

-- phone reste soumis a ck_users_phone_format ('^\+[1-9][0-9]{7,14}$') et
-- VARCHAR(20) : impossible d'y ecrire un placeholder textuel ("deleted-...").
-- Cette sequence fournit un numero synthetique garanti unique au format
-- attendu ("+9" + 14 chiffres, jamais un indicatif pays reel) pour liberer
-- le vrai numero de l'utilisateur (voir User#anonymizeForDeletion).
CREATE SEQUENCE deleted_account_phone_seq START WITH 1;
