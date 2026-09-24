-- =====================================================================
-- V15 — Alignement de type sur idempotency_keys.request_hash (mission
-- de durcissement de la logique metier, voir docs/AUDIT_BUSINESS_LOGIC.md §17)
--
-- La table idempotency_keys est amorcee par V1 mais n'a jamais ete
-- consommee par du code applicatif jusqu'a cette phase (voir
-- docs/BACKEND.md §20, ecart #1). Son activation reelle par
-- common/idempotency/IdempotencyKey introduit un mapping JPA String
-- standard, qui suppose VARCHAR — exactement le meme ajustement deja
-- applique sans probleme a payment_proofs.checksum_sha256 et
-- settlement_proofs.checksum_sha256 par les migrations V10/V11 (elles
-- aussi initialement CHAR(64) en V1).
--
-- CHAR(64) -> VARCHAR(64) est un elargissement de type strict (meme
-- longueur maximale, memes valeurs SHA-256 hexadecimales de 64
-- caracteres) : aucune donnee existante n'est tronquee ni perdue. Cette
-- table n'ayant jamais ete alimentee par du code applicatif avant cette
-- phase, elle est de toute facon vide en production a ce stade.
-- =====================================================================

ALTER TABLE idempotency_keys
    ALTER COLUMN request_hash TYPE VARCHAR(64);
