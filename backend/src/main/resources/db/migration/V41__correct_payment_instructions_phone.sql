-- =====================================================================
-- V41 — corrige le numero Mobile Money affiche au client avant paiement
-- (retour client oct. 2026 : "le numero sur lequel on envoit l'argent
-- c'est un faux, le bon c'est +226 65 38 23 37").
--
-- V39 a seede ce texte avec un numero errone (+226 71 00 25 25). Cette
-- valeur est administrable en base (PUT /api/admin/settings/PAYMENT_
-- INSTRUCTIONS_TEXT) : un administrateur a donc pu deja la corriger
-- manuellement depuis le seed initial -- WHERE value = l'ancien texte
-- exact evite d'ecraser une correction/personnalisation deja faite en
-- prod. Une migration (plutot que de compter uniquement sur l'admin) car
-- V39 lui-meme est immuable : jamais de modification retroactive d'une
-- migration deja appliquee (voir DEPLOY.md), donc jamais moyen de
-- corriger la valeur seedee autrement qu'avec une nouvelle migration.
-- =====================================================================

UPDATE system_settings
SET value = 'Envoyez le montant indique par Mobile Money au +226 65 38 23 37, puis declarez votre paiement ci-dessous.',
    updated_at = now()
WHERE setting_key = 'PAYMENT_INSTRUCTIONS_TEXT'
  AND value = 'Envoyez le montant indique par Mobile Money au +226 71 00 25 25, puis declarez votre paiement ci-dessous.';
