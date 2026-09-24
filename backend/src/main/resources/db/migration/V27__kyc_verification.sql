-- =====================================================================
-- V27 — Verification d'identite (KYC), obligatoire au-dela d'un seuil
--
-- Volontairement minimal : un drapeau pose par un administrateur sur le compte
-- (qui, quand), pas un moteur de conformite complet -- aucun document/upload/OCR,
-- aucune verification RCCM/fiscale (hors perimetre, voir Phase 8).
--
-- Le seuil est configurable via system_settings (KYC_REQUIRED_THRESHOLD_XOF),
-- jamais code en dur -- meme convention que MIN_ORDER_AMOUNT_CFA/MAX_ORDER_AMOUNT_CFA.
-- =====================================================================

ALTER TABLE users
    ADD COLUMN kyc_verified    BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN kyc_verified_at TIMESTAMPTZ,
    ADD COLUMN kyc_verified_by UUID;

ALTER TABLE users
    ADD CONSTRAINT fk_users_kyc_verified_by FOREIGN KEY (kyc_verified_by) REFERENCES users (id);

INSERT INTO system_settings (setting_key, value, value_type, description, is_public, updated_at) VALUES
    ('KYC_REQUIRED_THRESHOLD_XOF', '300000', 'DECIMAL',
     'Montant XOF (inclus) a partir duquel la verification d''identite (KYC) devient obligatoire pour creer un ordre.',
     TRUE, now())
ON CONFLICT (setting_key) DO NOTHING;
