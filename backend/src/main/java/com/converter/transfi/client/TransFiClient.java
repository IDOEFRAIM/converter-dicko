package com.converter.transfi.client;

/**
 * Frontiere avec l'API TransFi BizPay — la seule classe autorisee a faire un appel reseau vers
 * TransFi (voir {@code docs/TRANSFI_INTEGRATION.md}). Une interface, pas seulement
 * {@link TransFiHttpClient} directement, pour permettre un double de test dans
 * {@code TransfiOrchestrationServiceTest} sans jamais toucher au reseau en test.
 */
public interface TransFiClient {

    /** Cree un ordre payin ou payout. Leve {@link TransFiApiException} si TransFi refuse ou est injoignable. */
    TransFiOrderResult createOrder(TransFiCreateOrderRequest request);

    /** Relit le statut courant d'un ordre deja cree — utilise pour la reconciliation manuelle (admin). */
    TransFiOrderResult getOrder(String providerOrderId);
}
