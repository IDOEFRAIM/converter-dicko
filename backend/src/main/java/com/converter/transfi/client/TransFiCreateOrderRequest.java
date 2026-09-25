package com.converter.transfi.client;

import java.math.BigDecimal;

/**
 * Intention d'ordre payin/payout, independante du format exact attendu par TransFi — c'est
 * {@link TransFiHttpClient} qui la traduit vers le payload JSON reel (voir sa Javadoc : contrat
 * PROVISOIRE, a confirmer avant activation en production).
 *
 * @param type              PAYIN ou PAYOUT
 * @param amount            montant a encaisser (PAYIN, en {@code currency}) ou a decaisser
 *                          (PAYOUT, en {@code currency})
 * @param currency          devise de {@code amount} (ex. XOF pour un payin, CNY/USDT pour un payout)
 * @param customerReference notre reference interne (Order.reference) — permet de retrouver
 *                          l'ordre cote TransFi independamment de son identifiant
 * @param beneficiaryName   nom du beneficiaire (PAYOUT uniquement, ignore pour un PAYIN)
 * @param beneficiaryAccount identifiant de compte/wallet du beneficiaire (PAYOUT uniquement)
 */
public record TransFiCreateOrderRequest(TransFiOrderType type, BigDecimal amount, String currency,
                                         String customerReference, String beneficiaryName,
                                         String beneficiaryAccount) {
}
