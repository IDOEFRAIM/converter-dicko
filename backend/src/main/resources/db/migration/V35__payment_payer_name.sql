-- Nom du payeur, en complement du numero deja present (retour client : le
-- numero doit devenir obligatoire a la declaration, et le nom aussi -- les
-- deux servent a l'administrateur pour rapprocher la preuve du virement
-- Mobile Money/bancaire reel). Colonne nullable : les paiements deja
-- declares avant cette version n'ont ni l'un ni l'autre retroactivement ;
-- l'obligation est imposee cote applicatif (SubmitPaymentRequest), jamais en
-- reecrivant l'historique.
ALTER TABLE payments ADD COLUMN payer_name VARCHAR(160);
