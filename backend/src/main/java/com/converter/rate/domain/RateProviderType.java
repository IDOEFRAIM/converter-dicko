package com.converter.rate.domain;

/**
 * Type de source de cotation.
 *
 * <p>Seul {@code MANUAL} est operationnel dans ce MVP. {@code MARKET}
 * et {@code P2P} sont des noms reserves dans l'abstraction
 * {@link com.converter.rate.provider.RateProvider} : leur integration
 * (API de marche, echanges P2P type Binance/OKX) est explicitement
 * hors perimetre de cette phase, et leur activation operationnelle
 * devra etre validee au regard du cadre reglementaire applicable avant
 * toute mise en production (voir docs/ARCHITECTURE.md, Partie I, J.3).
 */
public enum RateProviderType {
    MANUAL,
    MARKET,
    P2P
}
