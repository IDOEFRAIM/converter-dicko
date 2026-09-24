-- =====================================================================
-- V26 — Profil professionnel (Phase 8, evolution Burkina Faso <-> Chine)
--
-- Aucune colonne accountType sur `users` (decision d'architecture validee) : le statut
-- BUSINESS d'un utilisateur est entierement determine par l'existence d'une ligne ici
-- (UNIQUE(user_id)) — voir com.converter.business.profile.service.BusinessProfileService.
--
-- Donnee de profil pure, sans lien avec le pipeline financier (Quote/Order/Payment/
-- Settlement/Treasury/Wallet) : aucune colonne, aucune contrainte ne reference ces tables.
--
-- `registration_number` reste nullable : ce n'est pas un moteur de conformite KYC entreprise
-- (aucune verification RCCM/fiscale, aucun statut PENDING_KYC/VERIFIED/REJECTED/SUSPENDED —
-- hors perimetre volontaire de cette phase).
-- =====================================================================

CREATE TABLE business_profiles (
    id                   UUID          NOT NULL DEFAULT gen_random_uuid(),
    user_id              UUID          NOT NULL,
    business_name        VARCHAR(160)  NOT NULL,
    business_type        VARCHAR(24)   NOT NULL,
    registration_number  VARCHAR(60),
    country              VARCHAR(100)  NOT NULL,
    city                 VARCHAR(100),
    address              VARCHAR(255),
    created_at           TIMESTAMPTZ   NOT NULL,
    updated_at           TIMESTAMPTZ   NOT NULL,
    version              BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_business_profiles PRIMARY KEY (id),
    CONSTRAINT uq_business_profiles_user UNIQUE (user_id),
    CONSTRAINT ck_business_profiles_type CHECK (business_type IN ('IMPORTER', 'MERCHANT', 'SERVICES', 'OTHER')),
    CONSTRAINT fk_business_profiles_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- Aucun index supplementaire : UNIQUE(user_id) cree deja un index btree qui couvre l'unique
-- requete de lecture (findByUserId/existsByUserId).
