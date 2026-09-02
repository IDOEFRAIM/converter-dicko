package com.converter.settlement.domain;

/**
 * Cycle de vie d'un reglement CNY — execution entierement manuelle.
 *
 * <pre>
 * PENDING --&gt; EXECUTED   (terminal — declenche Order -> COMPLETED et consomme la reservation)
 * </pre>
 */
public enum SettlementStatus {
    PENDING,
    EXECUTED
}
