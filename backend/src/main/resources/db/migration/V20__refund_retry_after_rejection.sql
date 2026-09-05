-- =====================================================================
-- V20 — Un remboursement REJECTED n'est plus une impasse definitive
--
-- Decision de la mission "Refund retry policy" (audit de fermeture) :
-- rejeter un Refund est une decision ADMINISTRATIVE (ex. mauvais paiement
-- selectionne, besoin de plus d'information), pas un fait objectif comme
-- un montant de paiement errone (contrairement au rejet d'un Payment,
-- terminal par decision explicite — voir V10). Bloquer definitivement
-- toute nouvelle tentative de remboursement pour ce paiement apres un
-- simple REJECTED forcerait une intervention manuelle en base pour une
-- erreur d'appreciation corrigeable.
--
-- L'invariant qui compte reellement reste intact : au plus UN
-- remboursement ACTIF (PENDING ou PROCESSED) a la fois par paiement —
-- jamais deux decaissements XOF pour le meme droit au remboursement.
-- Un historique de tentatives REJECTED, en revanche, n'est plus borne.
--
-- Meme pattern deja utilise dans ce schema pour "au plus un ACTIF, mais
-- un historique illimite" : uq_rate_source_current (V7, WHERE effective_to
-- IS NULL), uq_treasury_tx_reservation_per_order / uq_treasury_tx_
-- resolution_per_order (V16, WHERE type IN (...)).
-- =====================================================================

ALTER TABLE refunds DROP CONSTRAINT uq_refunds_payment;

CREATE UNIQUE INDEX uq_refunds_payment_active
    ON refunds (payment_id)
    WHERE status IN ('PENDING', 'PROCESSED');
