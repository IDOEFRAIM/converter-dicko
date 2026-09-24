-- =====================================================================
-- V31 — Recompense de Ruee progressive (remarque produit #4)
--
-- La reduction de marge accordee a la reussite d'une Ruee n'est plus un
-- pourcentage fixe : elle croit avec le NOMBRE de participants et la
-- SOMME TOTALE echangee par le groupe, jusqu'a un plafond.
--
--   reduction = base
--             + PAR_PARTICIPANT * (participants - 1)
--             + PAR_MILLION_XOF * floor(volume_cumule / 1 000 000)
--   bornee a  [0, MAX]
--
-- `POOL_REWARD_MARGIN_REDUCTION_PERCENTAGE` (amorce en V29) devient la
-- part *de base* de cette formule — cle et valeur inchangees, seule sa
-- semantique evolue. Trois nouvelles cles portent les coefficients ;
-- toutes restent administrables via system_settings sans redeploiement.
--
-- Aucune Ruee existante n'est touchee : le calcul est fait a la lecture
-- (affichage) et au moment de la reussite (octroi), jamais fige a la
-- creation au-dela de la part de base deja snapshotee sur `pools`.
-- =====================================================================

INSERT INTO system_settings (setting_key, value, value_type, description, is_public, updated_at) VALUES
    ('POOL_REWARD_PER_PARTICIPANT_PCT', '0.15', 'DECIMAL',
     'Points de marge ajoutes a la recompense de Ruee par participant au-dela du premier.',
     FALSE, now()),

    ('POOL_REWARD_PER_MILLION_XOF_PCT', '0.10', 'DECIMAL',
     'Points de marge ajoutes a la recompense de Ruee par tranche pleine de 1 000 000 XOF echanges par le groupe.',
     FALSE, now()),

    ('POOL_REWARD_MAX_PCT', '2.0', 'DECIMAL',
     'Plafond de la reduction de marge accordee par une Ruee reussie, tous bonus cumules.',
     FALSE, now())
ON CONFLICT (setting_key) DO NOTHING;

UPDATE system_settings
   SET description = 'Part de BASE de la reduction de marge d''une Ruee reussie (voir POOL_REWARD_PER_PARTICIPANT_PCT / _PER_MILLION_XOF_PCT / _MAX_PCT).'
 WHERE setting_key = 'POOL_REWARD_MARGIN_REDUCTION_PERCENTAGE';
