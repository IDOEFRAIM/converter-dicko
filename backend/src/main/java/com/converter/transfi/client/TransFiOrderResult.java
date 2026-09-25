package com.converter.transfi.client;

/**
 * Reponse normalisee d'une creation/consultation d'ordre TransFi — traduite depuis le JSON brut
 * de TransFi par {@link TransFiHttpClient}, jamais expose au-dela de ce module sous sa forme
 * d'origine (voir {@code docs/TRANSFI_INTEGRATION.md}).
 *
 * @param providerOrderId identifiant de l'ordre cote TransFi
 * @param rawStatus       statut brut renvoye par TransFi, TEL QUEL (le vocabulaire exact —
 *                        valeurs possibles, casse -- n'est pas confirme ; la traduction vers
 *                        {@link com.converter.transfi.domain.TransfiOrderStatus} se fait au plus
 *                        pres de l'appelant, jamais ici)
 * @param payUrl          lien de paiement, uniquement pertinent pour un PAYIN
 * @param rawJson          corps de reponse brut, conserve pour {@code TransfiOrder.rawPayload}
 */
public record TransFiOrderResult(String providerOrderId, String rawStatus, String payUrl, String rawJson) {
}
