-- =====================================================================
-- V39 — Instructions de paiement affichees au client (retour beta-testeur
-- sept. 2026 : "je suis arrive jusqu'a faire l'order... je sais meme pas
-- comment payer" -- aucune donnee de destination de paiement n'existait
-- nulle part cote serveur avant cette migration).
--
-- Texte libre, administrable sans redeploiement (ex. numero Mobile Money,
-- nom du titulaire, coordonnees bancaires). Volontairement une simple
-- cle STRING, pas une structure dediee : le contenu varie selon les
-- moyens de paiement actifs (ENABLED_PAYMENT_METHODS) et sa mise en
-- forme reste a la discretion de l'administration.
--
-- Public (is_public = TRUE) : affiche au client AVANT qu'il declare son
-- paiement (PaymentSubmitPage), via /api/settings/public — jamais une
-- regle metier, uniquement un texte d'aide.
-- =====================================================================

INSERT INTO system_settings (setting_key, value, value_type, description, is_public, updated_at) VALUES
    ('PAYMENT_INSTRUCTIONS_TEXT',
     'Envoyez le montant indique par Mobile Money au +226 71 00 25 25, puis declarez votre paiement ci-dessous.',
     'STRING',
     'Instructions affichees au client pour savoir ou/comment envoyer son paiement (Mobile Money, banque...) avant de le declarer.',
     TRUE, now())
ON CONFLICT (setting_key) DO NOTHING;
