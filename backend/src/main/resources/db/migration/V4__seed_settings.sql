-- =====================================================================
-- V4 — Amorcage des parametres metier
--
-- Aucune de ces valeurs n'est codee en dur dans le code Java :
-- `SettingsService` est la seule source de lecture, et l'administrateur
-- peut les modifier a chaud via PUT /api/admin/settings/{key}.
--
-- `is_public = TRUE` autorise l'exposition sur GET /api/settings/public
-- (endpoint anonyme). Tout parametre revelant une strategie interne
-- reste prive.
-- =====================================================================

INSERT INTO system_settings (setting_key, value, value_type, description, is_public, updated_at) VALUES
    ('MIN_ORDER_AMOUNT_CFA', '10000', 'DECIMAL',
     'Montant minimum d''un ordre, en CFA', TRUE, now()),

    ('MAX_ORDER_AMOUNT_CFA', '2000000', 'DECIMAL',
     'Montant maximum d''un ordre, en CFA', TRUE, now()),

    ('RATE_LOCK_DURATION_MINUTES', '30', 'INTEGER',
     'Duree de verrouillage du taux apres creation d''un ordre, en minutes', TRUE, now()),

    ('ORDER_AUTO_EXPIRE_ENABLED', 'true', 'BOOLEAN',
     'Active le job d''expiration automatique des ordres non payes', FALSE, now()),

    ('REQUIRE_PAYMENT_PROOF', 'true', 'BOOLEAN',
     'Exige au moins une preuve de paiement avant validation administrateur', TRUE, now()),

    ('TREASURY_RESERVE_ON_ORDER', 'true', 'BOOLEAN',
     'Reserve la liquidite CNY des la creation de l''ordre', FALSE, now()),

    ('MAX_PROOF_FILE_SIZE_BYTES', '5242880', 'INTEGER',
     'Taille maximale d''une preuve de paiement, en octets (5 Mo)', TRUE, now()),

    ('MAX_PROOFS_PER_PAYMENT', '3', 'INTEGER',
     'Nombre maximal de preuves attachees a un paiement', TRUE, now()),

    ('ENABLED_PAYMENT_METHODS', 'MOBILE_MONEY', 'STRING',
     'Moyens de paiement actifs, separes par des virgules', TRUE, now()),

    ('MAX_OPEN_ORDERS_PER_USER', '3', 'INTEGER',
     'Nombre maximal d''ordres simultanement en attente de paiement par client', FALSE, now())
ON CONFLICT (setting_key) DO NOTHING;
