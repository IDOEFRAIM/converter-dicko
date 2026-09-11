-- Connexion/inscription via Google (retour client : "c'est plus simple").
-- Le numero de telephone reste l'identifiant central de la plateforme
-- (KYC, recherche admin, notifications, unicite) : un compte cree via
-- Google le fournit dans un second temps (voir AuthService.completeGoogleSignUp),
-- jamais optionnel. Seul le mot de passe devient facultatif -- un compte
-- Google n'en a jamais.
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

ALTER TABLE users ADD COLUMN google_subject VARCHAR(255);

ALTER TABLE users ADD CONSTRAINT uq_users_google_subject UNIQUE (google_subject);

-- Un compte doit toujours porter au moins un moyen de connexion.
ALTER TABLE users ADD CONSTRAINT ck_users_has_auth_method
    CHECK (password_hash IS NOT NULL OR google_subject IS NOT NULL);
