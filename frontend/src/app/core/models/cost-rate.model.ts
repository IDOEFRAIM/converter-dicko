/**
 * Configuration du coût de revient XOF → USD → CNY (`daily_cost_rate_configurations`).
 *
 * <p><b>Réservé à l'administration.</b> Depuis la Phase 3.1 (migration V18), c'est CETTE
 * configuration — et non le `RateSource` publié via « Taux de change » — qui sert de base au
 * pricing des devis : un `Quote` fige le `breakEvenRate` calculé ici, auquel le backend
 * applique ensuite la marge commerciale. Tant qu'aucune configuration n'a été publiée, aucun
 * devis ne peut être créé côté client.
 *
 * <p>`breakEvenRate` et ses paramètres sont des données internes confidentielles : aucun DTO
 * public ne les porte.
 */
export interface CostRateConfiguration {
  id: string;
  /** Jour auquel ces paramètres s'appliquent (format `YYYY-MM-DD`). */
  businessDate: string;
  /** Taux XOF pour 1 USD. */
  rateXofUsd: string;
  /** Taux CNY pour 1 USD. */
  rateUsdCny: string;
  /** Frais proportionnels XOF → USD, en fraction décimale (`0.01` = 1 %). */
  feeXofUsdPercent: string;
  /** Frais fixe USD → CNY, en USD. */
  feeUsdCnyFixedUsd: string;
  /** Montant XOF de référence utilisé pour dériver le `breakEvenRate`. */
  referenceAmountXof: string;
  /** Coût de revient calculé, en XOF par CNY. */
  breakEvenRate: string;
  /**
   * Marge commerciale actuellement active (`DEFAULT_MARGIN_PERCENTAGE`), recalculée à la
   * lecture — jamais persistée sur cette configuration elle-même.
   */
  marginPercentage: string;
  /**
   * `breakEvenRate` après application de `marginPercentage` — exactement le nombre que le
   * client voit (mobile/web), calculé avec la même formule (`RateEngine.applyMargin`) que celle
   * utilisée pour un vrai devis. Sert à ne plus confondre cette page avec l'écran distinct
   * « Taux préférentiel » (RateSource), sans lien avec le pricing des devis.
   */
  customerRate: string;
  note: string | null;
  createdAt: string;
}

export interface PublishCostRateConfigurationRequest {
  businessDate: string;
  rateXofUsd: string;
  rateUsdCny: string;
  feeXofUsdPercent: string;
  feeUsdCnyFixedUsd: string;
  referenceAmountXof: string;
  note: string | null;
}
