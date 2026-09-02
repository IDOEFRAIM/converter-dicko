-- =====================================================================
-- V5 — Amorcage des comptes de tresorerie
--
-- Un compte par devise, soldes a zero. Les mouvements reels sont
-- introduits par l'administrateur via le module `treasury` (Phase 6).
-- =====================================================================

INSERT INTO treasury_accounts (currency, balance, reserved_balance, low_threshold, updated_at)
VALUES
    ('XOF', 0, 0, 0, now()),
    ('CNY', 0, 0, 0, now())
ON CONFLICT (currency) DO NOTHING;
