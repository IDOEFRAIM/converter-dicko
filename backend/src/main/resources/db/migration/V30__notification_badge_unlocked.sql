-- =====================================================================
-- V30 — notifications : nouveau type BADGE_UNLOCKED (mission
-- "differenciation marketing" : celebrer le franchissement d'un palier de
-- badge, jamais seulement l'afficher au prochain chargement de "Mes
-- gains") — meme patron d'extension additive que V25/V29 (DROP puis ADD,
-- Postgres n'autorisant pas de modifier un CHECK existant).
-- =====================================================================

ALTER TABLE notifications DROP CONSTRAINT ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN (
    'QUOTE_CREATED', 'PAYMENT_SUBMITTED', 'PAYMENT_CONFIRMED',
    'EXCHANGE_STARTED', 'EXCHANGE_PROGRESS', 'EXCHANGE_COMPLETED',
    'PREFERRED_RATE_REACHED', 'PREFERRED_RATE_EXPIRED', 'EXCHANGE_CANCELLED',
    'ORDER_EXPIRED', 'RATE_ALERT_TRIGGERED', 'POOL_SUCCEEDED', 'POOL_EXPIRED',
    'BADGE_UNLOCKED'));
