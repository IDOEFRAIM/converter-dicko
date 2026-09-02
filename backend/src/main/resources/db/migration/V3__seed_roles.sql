-- =====================================================================
-- V3 — Amorcage des roles
--
-- Les identifiants sont fixes explicitement (et non issus d'une
-- sequence) : ils sont referenceurs stables entre environnements.
-- =====================================================================

INSERT INTO roles (id, code, label) VALUES
    (1, 'USER',  'Client'),
    (2, 'ADMIN', 'Administrateur')
ON CONFLICT (id) DO NOTHING;
