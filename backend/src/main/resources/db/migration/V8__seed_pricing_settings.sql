-- =====================================================================
-- V8 — Amorcage des parametres de tarification (Phase 3)
--
-- `RATE_LOCK_DURATION_MINUTES` (deja amorce en V4, valeur 30) est
-- reutilise tel quel comme duree de validite d'un Quote : aucune
-- nouvelle cle n'est necessaire pour cette duree.
--
-- La marge et les frais sont deux parametres distincts (jamais
-- fusionnes) : voir docs/ARCHITECTURE.md, Partie I, section G.4.
-- =====================================================================

INSERT INTO system_settings (setting_key, value, value_type, description, is_public, updated_at) VALUES
    ('DEFAULT_MARGIN_PERCENTAGE', '1.5000', 'DECIMAL',
     'Marge appliquee sur le taux de marche pour obtenir le taux client, en pourcentage', FALSE, now()),

    ('DEFAULT_FEE_PERCENTAGE', '0.0000', 'DECIMAL',
     'Frais de service proportionnels au montant XOF, en pourcentage (distinct de la marge de change)', FALSE, now()),

    ('DEFAULT_FIXED_FEE_XOF', '0', 'DECIMAL',
     'Frais de service fixes, en XOF, ajoutes au frais proportionnel', FALSE, now())
ON CONFLICT (setting_key) DO NOTHING;
