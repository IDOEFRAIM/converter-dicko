package com.converter.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Verification anticipee, cote client, qu'un ordre pourra etre cree a partir d'un devis
 * accepte — avant que l'utilisateur ne saisisse le beneficiaire.
 *
 * <p>N'expose <b>aucun</b> solde de tresorerie : uniquement un booleen et le montant CNY qui
 * figure deja sur le devis de l'utilisateur. La verite reste la reservation effectuee a la
 * creation de l'ordre ({@code OrderService.create} -&gt; {@code TreasuryService.reserve}) ;
 * ceci n'est qu'un indicateur d'affichage, susceptible d'evoluer d'ici la creation reelle.
 *
 * @param quoteId                     devis concerne
 * @param amountCny                   montant CNY a regler (copie du devis)
 * @param settlementReservationEnabled {@code true} si {@code TREASURY_RESERVE_ON_ORDER} est actif
 *                                     (sinon aucune liquidite n'est verifiee a la creation)
 * @param sufficientLiquidity         {@code true} si la liquidite CNS disponible couvre
 *                                     actuellement {@code amountCny} (ou si la reservation est
 *                                     desactivee)
 */
@Schema(description = "Indicateur de faisabilite d'un ordre a partir d'un devis, avant saisie du beneficiaire")
public record OrderFeasibilityResponse(
        UUID quoteId,
        BigDecimal amountCny,
        boolean settlementReservationEnabled,
        boolean sufficientLiquidity
) {
}
