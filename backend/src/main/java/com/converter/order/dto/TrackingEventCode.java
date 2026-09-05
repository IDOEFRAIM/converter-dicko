package com.converter.order.dto;

/**
 * Catalogue ferme des codes d'evenement de la timeline de suivi (Phase 3). Contrat stable pour
 * le frontend — {@code label} (voir {@link TrackingEvent}) n'est qu'une commodite d'affichage,
 * jamais une source de verite : c'est {@code code} que le frontend doit tester.
 *
 * <p>Deux familles :
 * <ul>
 *   <li>Codes qui correspondent 1:1 a une valeur de {@code OrderStatus} (derives directement
 *       d'une ligne {@code OrderStatusHistory}, la source de verite des transitions) ;</li>
 *   <li>Codes d'enrichissement ({@code PAYMENT_REJECTED}, {@code SETTLEMENT_EXECUTED},
 *       {@code REFUND_PENDING}, {@code REFUND_PROCESSED}) qui n'existent dans aucune ligne
 *       {@code OrderStatusHistory} — {@code OrderStatus} ne modelise ni "paiement rejete" ni
 *       "reglement execute" ni "rembourse" comme des etats de l'ordre — mais que
 *       {@code Payment}/{@code Settlement}/{@code Refund} permettent de dater reellement.
 *       Voir {@code OrderTrackingService} pour la regle exacte d'ajout (jamais de duplication).</li>
 * </ul>
 */
public enum TrackingEventCode {
    ORDER_CREATED,
    PAYMENT_SUBMITTED,
    PAYMENT_VERIFIED,
    PAYMENT_REJECTED,
    PROCESSING,
    SETTLEMENT_EXECUTED,
    COMPLETED,
    CANCELLED,
    REJECTED,
    EXPIRED,
    REFUND_PENDING,
    REFUND_PROCESSED
}
