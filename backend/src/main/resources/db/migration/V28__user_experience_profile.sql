-- =====================================================================
-- V28 — Profil d'experience marketing (PRO / STUDENT_MALE / STUDENT_FEMALE)
--
-- Distinct de business_profiles (V26, Personnel vs Professionnel) : ce champ
-- ne change jamais le moteur de change, les taux ni les frais -- il pilote
-- uniquement l'habillage cote mobile (couleurs, badges, ton des notifications).
-- Auto-selectionnable par le client lui-meme (aucun controle administrateur),
-- au contraire de kyc_verified.
--
-- Defaut PRO : un compte existant ou cree sans preference explicite garde le
-- design sobre actuel plutot que de basculer silencieusement vers un theme
-- "etudiant" jamais choisi.
-- =====================================================================

ALTER TABLE users
    ADD COLUMN experience_profile VARCHAR(20) NOT NULL DEFAULT 'PRO';

ALTER TABLE users
    ADD CONSTRAINT ck_users_experience_profile
        CHECK (experience_profile IN ('PRO', 'STUDENT_MALE', 'STUDENT_FEMALE'));
