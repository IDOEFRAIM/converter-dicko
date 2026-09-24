-- =====================================================================
-- V2 — Index et contraintes d'unicite metier
--
-- Les index UNIQUE PARTIELS de ce fichier ne sont pas des optimisations :
-- ce sont des INVARIANTS METIER appliques par la base. Ils rendent
-- certaines fautes financieres physiquement impossibles, meme si le
-- code applicatif comporte un defaut ou si deux requetes concurrentes
-- franchissent les memes verifications applicatives.
-- =====================================================================

-- ---------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------
CREATE UNIQUE INDEX uq_users_email ON users (email) WHERE email IS NOT NULL;
CREATE INDEX idx_users_status_created ON users (status, created_at DESC);

-- ---------------------------------------------------------------------
-- user_roles
-- ---------------------------------------------------------------------
CREATE INDEX idx_user_roles_role ON user_roles (role_id);

-- ---------------------------------------------------------------------
-- exchange_rates
--
-- INVARIANT : il ne peut jamais exister deux taux courants simultanes.
-- L'index porte sur une expression constante restreinte aux lignes
-- courantes : au plus une ligne peut satisfaire `effective_to IS NULL`.
-- Deux POST concurrents => le second echoue en contrainte d'unicite.
-- ---------------------------------------------------------------------
CREATE UNIQUE INDEX uq_exchange_rate_current
    ON exchange_rates ((effective_to IS NULL))
    WHERE effective_to IS NULL;

CREATE INDEX idx_exchange_rates_effective_from ON exchange_rates (effective_from DESC);

-- ---------------------------------------------------------------------
-- orders
-- ---------------------------------------------------------------------
CREATE INDEX idx_orders_user_created ON orders (user_id, created_at DESC);
CREATE INDEX idx_orders_status_created ON orders (status, created_at DESC);

-- Index partiel dedie au job d'expiration : seuls les ordres reellement
-- expirables sont indexes, le balayage periodique reste minimal.
CREATE INDEX idx_orders_expiry
    ON orders (rate_expires_at)
    WHERE status = 'AWAITING_PAYMENT';

-- ---------------------------------------------------------------------
-- beneficiaries
-- ---------------------------------------------------------------------
CREATE INDEX idx_beneficiaries_identifier ON beneficiaries (identifier);

-- ---------------------------------------------------------------------
-- payments
-- ---------------------------------------------------------------------
CREATE INDEX idx_payments_order ON payments (order_id);

-- File d'attente de verification administrateur.
CREATE INDEX idx_payments_pending
    ON payments (submitted_at)
    WHERE status = 'SUBMITTED';

-- INVARIANT : un seul paiement vivant par ordre. Un paiement rejete
-- libere le verrou et autorise une nouvelle soumission.
CREATE UNIQUE INDEX uq_payments_active_per_order
    ON payments (order_id)
    WHERE status <> 'REJECTED';

-- INVARIANT : une reference de transaction Mobile Money ne peut pas
-- servir sur deux ordres differents.
CREATE UNIQUE INDEX uq_payments_txref
    ON payments (method, transaction_reference)
    WHERE status <> 'REJECTED';

-- ---------------------------------------------------------------------
-- payment_proofs
-- ---------------------------------------------------------------------
CREATE INDEX idx_payment_proofs_payment ON payment_proofs (payment_id);

-- ---------------------------------------------------------------------
-- treasury_transactions
--
-- INVARIANT LE PLUS IMPORTANT DU SCHEMA :
-- un ordre ne peut jamais etre reserve deux fois, libere deux fois,
-- ni decaisse deux fois. C'est le filet de securite ultime contre le
-- double decaissement — il tient meme si les verrous applicatifs,
-- l'idempotence HTTP et la machine d'etat echouent tous les trois.
-- ---------------------------------------------------------------------
CREATE UNIQUE INDEX uq_treasury_tx_order_type
    ON treasury_transactions (order_id, type)
    WHERE order_id IS NOT NULL
      AND type IN ('RESERVATION', 'RELEASE', 'WITHDRAWAL');

CREATE INDEX idx_treasury_tx_account_created ON treasury_transactions (account_id, created_at DESC);
CREATE INDEX idx_treasury_tx_order ON treasury_transactions (order_id);

-- ---------------------------------------------------------------------
-- order_status_history
-- ---------------------------------------------------------------------
CREATE INDEX idx_osh_order_created ON order_status_history (order_id, created_at);

-- ---------------------------------------------------------------------
-- audit_logs
-- ---------------------------------------------------------------------
CREATE INDEX idx_audit_created ON audit_logs (created_at DESC);
CREATE INDEX idx_audit_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_actor ON audit_logs (actor_id, created_at DESC);
CREATE INDEX idx_audit_action ON audit_logs (action, created_at DESC);

-- ---------------------------------------------------------------------
-- idempotency_keys
-- ---------------------------------------------------------------------
CREATE INDEX idx_idempotency_expiry ON idempotency_keys (expires_at);
