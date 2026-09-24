-- =====================================================================
-- V32 — Dossiers de verification d'identite (KYC) soumis par l'utilisateur
--       (remarque produit #6).
--
-- V27 avait pose un simple drapeau administrateur (users.kyc_verified). On
-- ajoute ici le PARCOURS UTILISATEUR : l'utilisateur televerse une piece
-- d'identite (+ verso) et un selfie ; un administrateur approuve ou rejette.
-- L'approbation appelle le meme User#verifyKyc que la voie admin directe —
-- users.kyc_verified reste la seule source de verite consommee par
-- OrderService (aucune double logique).
--
-- Revue MANUELLE interne : aucun prestataire tiers, aucun OCR. Les fichiers
-- sont stockes via FileStorageService (repertoire "kyc"), jamais en base.
-- =====================================================================

CREATE TABLE kyc_submissions (
    id                UUID PRIMARY KEY,
    user_id           UUID NOT NULL REFERENCES users (id),
    document_type     VARCHAR(24) NOT NULL,
    doc_front_key     VARCHAR(255) NOT NULL,
    doc_back_key      VARCHAR(255),
    selfie_key        VARCHAR(255) NOT NULL,
    status            VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    submitted_at      TIMESTAMPTZ NOT NULL,
    reviewed_at       TIMESTAMPTZ,
    reviewed_by       UUID REFERENCES users (id),
    rejection_reason  VARCHAR(500),
    version           BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT ck_kyc_submissions_document_type
        CHECK (document_type IN ('NATIONAL_ID', 'PASSPORT', 'RESIDENCE_PERMIT')),
    CONSTRAINT ck_kyc_submissions_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    -- Un dossier est revu si et seulement si son statut n'est plus PENDING.
    CONSTRAINT ck_kyc_submissions_reviewed
        CHECK ((status = 'PENDING') = (reviewed_at IS NULL AND reviewed_by IS NULL)),
    -- Un motif de rejet n'existe que sur un dossier rejete.
    CONSTRAINT ck_kyc_submissions_rejection
        CHECK (status = 'REJECTED' OR rejection_reason IS NULL)
);

CREATE INDEX idx_kyc_submissions_user ON kyc_submissions (user_id, submitted_at DESC);
CREATE INDEX idx_kyc_submissions_status ON kyc_submissions (status, submitted_at);

-- Au plus un dossier en attente d'examen par utilisateur.
CREATE UNIQUE INDEX uq_kyc_submissions_one_pending
    ON kyc_submissions (user_id) WHERE status = 'PENDING';
