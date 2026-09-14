-- Bug reel en production : un beneficiaire ALIPAY/WECHAT_PAY s'identifie par un code QR (image,
-- voir beneficiaries.qr_code_storage_key, V36) et n'a donc plus forcement d'identifiant texte --
-- mais settlements.beneficiary_identifier (simple copie au moment de la creation du reglement)
-- etait reste NOT NULL, faisant echouer l'insertion (23502) pour tout ordre de ce type. L'echec
-- etait a tort rapporte comme "un reglement existe deja" (voir SettlementService#create : le
-- catch DataIntegrityViolationException ne distinguait pas la cause reelle).

ALTER TABLE settlements ALTER COLUMN beneficiary_identifier DROP NOT NULL;
