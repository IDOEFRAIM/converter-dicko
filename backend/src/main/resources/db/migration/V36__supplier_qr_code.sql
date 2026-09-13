-- Alipay/WeChat Pay s'identifient par un CODE QR (une image), jamais par un texte saisi au
-- clavier -- account_number/identifier restait jusqu'ici le seul champ possible pour ces types,
-- ce qui forcait a saisir un "identifiant" fictif a la place d'un vrai code QR.

ALTER TABLE suppliers ALTER COLUMN account_number DROP NOT NULL;
ALTER TABLE suppliers ADD COLUMN qr_code_storage_key VARCHAR(255);
ALTER TABLE suppliers ADD COLUMN qr_code_file_name VARCHAR(255);
ALTER TABLE suppliers ADD COLUMN qr_code_content_type VARCHAR(100);
ALTER TABLE suppliers ADD COLUMN qr_code_size_bytes BIGINT;

ALTER TABLE beneficiaries ALTER COLUMN identifier DROP NOT NULL;
ALTER TABLE beneficiaries ADD COLUMN qr_code_storage_key VARCHAR(255);
ALTER TABLE beneficiaries ADD COLUMN qr_code_file_name VARCHAR(255);
ALTER TABLE beneficiaries ADD COLUMN qr_code_content_type VARCHAR(100);
ALTER TABLE beneficiaries ADD COLUMN qr_code_size_bytes BIGINT;
