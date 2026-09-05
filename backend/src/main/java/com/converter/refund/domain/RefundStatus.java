package com.converter.refund.domain;

/**
 * Cycle de vie d'un remboursement — MVP entierement manuel, meme forme que {@code SettlementStatus}
 * (decision de rembourser, distincte du mouvement d'argent reel qui suit) :
 *
 * <pre>
 * PENDING --+--&gt; PROCESSED  (terminal — decaissement XOF reel enregistre, reference obligatoire)
 *           `--&gt; REJECTED   (terminal — l'admin revient sur la decision avant tout decaissement)
 * </pre>
 *
 * Aucun etat {@code FAILED} : une operation qui ne peut pas aboutir (ex. solde XOF insuffisant)
 * echoue et annule toute la transaction (voir {@code RefundService}) plutot que de laisser une
 * ligne dans un etat d'echec — exactement comme le reste du backend (une creation d'Order qui
 * echoue ne laisse jamais d'Order "FAILED").
 */
public enum RefundStatus {
    PENDING,
    PROCESSED,
    REJECTED
}
