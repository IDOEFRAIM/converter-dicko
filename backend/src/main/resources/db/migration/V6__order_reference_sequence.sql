-- =====================================================================
-- V6 — Sequence des references d'ordre
--
-- Produit une reference lisible par un operateur support, du type
-- ORD-202608-000123, tout en conservant l'UUID comme identifiant
-- public non enumerable.
--
-- La sequence est un objet PostgreSQL : elle reste coherente meme
-- sous forte concurrence, sans verrou ni relecture de table.
-- =====================================================================

CREATE SEQUENCE order_reference_seq
    START WITH 1
    INCREMENT BY 1
    NO MAXVALUE
    CACHE 1;

CREATE OR REPLACE FUNCTION next_order_reference() RETURNS VARCHAR AS $$
    SELECT 'ORD-' || to_char(now() AT TIME ZONE 'UTC', 'YYYYMM')
                  || '-' || lpad(nextval('order_reference_seq')::TEXT, 6, '0');
$$ LANGUAGE SQL;
