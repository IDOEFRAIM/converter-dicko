package com.converter.rate.provider;

import com.converter.rate.domain.MarketRate;
import com.converter.rate.domain.RateProviderType;

/**
 * Port d'acces a une source de cotation de marche.
 *
 * <p>Le domaine (moteur de tarification, devis) ne depend jamais d'une
 * implementation concrete, uniquement de cette abstraction — c'est ce
 * qui permet d'ajouter une source de marche ou une source P2P plus
 * tard sans modifier {@link com.converter.rate.engine.RateEngine} ni
 * {@code Quote}.
 *
 * <p>Implementations prevues :
 * <pre>
 * RateProvider
 *     |-- ManualRateProvider   (MVP — seule implementation active)
 *     |-- MarketRateProvider   (futur — agregation d'API de marche)
 *     `-- P2PRateProvider      (futur — Binance/OKX ou equivalent)
 * </pre>
 *
 * <p><b>Aucune integration Binance/OKX/API de marche n'est developpee
 * dans cette phase.</b> Leur activation operationnelle devra en outre
 * etre validee au regard du cadre reglementaire applicable avant toute
 * mise en production (voir docs/ARCHITECTURE.md, Partie I, section J.3).
 */
public interface RateProvider {

    /** Seule paire de devises supportee par ce MVP. */
    String DEFAULT_CURRENCY_PAIR = "XOF/CNY";

    /**
     * Cotation actuellement en vigueur pour la paire de devises donnee.
     *
     * @throws com.converter.common.exception.BusinessException {@code RATE_SOURCE_UNAVAILABLE}
     *         si aucune cotation courante n'est configuree
     */
    MarketRate currentRate(String currencyPair);

    /**
     * Sonde la disponibilite d'une cotation courante sans lever d'exception.
     *
     * <p>Destine aux appelants qui doivent reagir a une absence de cotation comme a un etat
     * metier normal (ex. {@code PreferredRateScheduler} evaluant une demande alors qu'aucun taux
     * manuel n'a encore ete publie) plutot que comme a une erreur — {@link #currentRate}
     * reste la methode a utiliser des qu'un pricing reel doit etre calcule.
     */
    boolean isAvailable(String currencyPair);

    RateProviderType type();
}
