-- =====================================================================
-- V16 — Expiration d'ordre + durcissement d'invariants (passe 2)
--
-- Trois volets independants :
--   1. orders.payment_deadline_at : fenetre de paiement immuable par ordre.
--      Au-dela, un scheduler expire l'ordre et LIBERE sa reservation CNY
--      (docs/AUDIT_BUSINESS_LOGIC_PASS2.md, P2-1). Colonne STOCKEE (et non
--      derivee) : un changement ulterieur de ORDER_PAYMENT_WINDOW_MINUTES
--      ne doit jamais reexpirer retroactivement des ordres existants.
--   2. treasury_transactions : la resolution d'une reservation d'ordre
--      (RELEASE ou WITHDRAWAL) doit etre unique — jamais un RELEASE ET un
--      WITHDRAWAL, jamais deux fois (P2-4). L'ancien index uq_treasury_tx_
--      order_type (V2) n'empechait que les doublons du MEME type.
--   3. Nouveaux parametres metier (P2-1, P2-2, P2-7).
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. orders.payment_deadline_at
-- ---------------------------------------------------------------------
ALTER TABLE orders ADD COLUMN payment_deadline_at TIMESTAMPTZ;

-- Backfill : 720 minutes (= 12 h, valeur par defaut de
-- ORDER_PAYMENT_WINDOW_MINUTES) apres la creation. S'applique a toutes
-- les lignes ; seules celles encore AWAITING_PAYMENT seront reellement
-- balayees par le scheduler.
UPDATE orders
SET payment_deadline_at = created_at + INTERVAL '720 minutes'
WHERE payment_deadline_at IS NULL;

ALTER TABLE orders ALTER COLUMN payment_deadline_at SET NOT NULL;

-- Index partiel dedie au balayage d'expiration : seuls les ordres
-- reellement expirables sont indexes (meme principe que idx_orders_expiry
-- de la Phase 1, retire en V9 avec l'ancienne colonne rate_expires_at).
CREATE INDEX idx_orders_payment_deadline
    ON orders (payment_deadline_at)
    WHERE status = 'AWAITING_PAYMENT';

-- ---------------------------------------------------------------------
-- 2. treasury_transactions : resolution unique de la reservation
-- ---------------------------------------------------------------------
DROP INDEX uq_treasury_tx_order_type;

-- Au plus UNE reservation par ordre.
CREATE UNIQUE INDEX uq_treasury_tx_reservation_per_order
    ON treasury_transactions (order_id)
    WHERE order_id IS NOT NULL AND type = 'RESERVATION';

-- Au plus UNE resolution de reservation par ordre : soit liberee (RELEASE),
-- soit consommee (WITHDRAWAL) — jamais les deux, jamais deux fois.
CREATE UNIQUE INDEX uq_treasury_tx_resolution_per_order
    ON treasury_transactions (order_id)
    WHERE order_id IS NOT NULL AND type IN ('RELEASE', 'WITHDRAWAL');

-- ---------------------------------------------------------------------
-- 2b. notifications : nouveau type ORDER_EXPIRED
-- ---------------------------------------------------------------------
ALTER TABLE notifications DROP CONSTRAINT ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN (
    'QUOTE_CREATED', 'PAYMENT_SUBMITTED', 'PAYMENT_CONFIRMED',
    'EXCHANGE_STARTED', 'EXCHANGE_PROGRESS', 'EXCHANGE_COMPLETED',
    'PREFERRED_RATE_REACHED', 'PREFERRED_RATE_EXPIRED', 'EXCHANGE_CANCELLED',
    'ORDER_EXPIRED'));

-- ---------------------------------------------------------------------
-- 3. Nouveaux parametres metier
-- ---------------------------------------------------------------------
INSERT INTO system_settings (setting_key, value, value_type, description, is_public, updated_at) VALUES
    ('ORDER_PAYMENT_WINDOW_MINUTES', '720', 'INTEGER',
     'Fenetre de paiement d''un ordre, en minutes. Au-dela, l''ordre est expire et sa reservation CNY liberee.',
     TRUE, now()),

    ('PAYMENT_AMOUNT_TOLERANCE_XOF', '0', 'DECIMAL',
     'Ecart tolere, en XOF, entre le montant recu declare et le montant attendu d''un paiement. 0 = montant exact requis.',
     TRUE, now()),

    ('RATE_MAX_AGE_MINUTES', '0', 'INTEGER',
     'Age maximal, en minutes, d''une cotation encore consideree comme courante. 0 = aucun controle de fraicheur.',
     FALSE, now())
ON CONFLICT (setting_key) DO NOTHING;
