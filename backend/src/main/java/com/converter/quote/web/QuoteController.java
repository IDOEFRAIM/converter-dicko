package com.converter.quote.web;

import com.converter.common.api.ApiResponse;
import com.converter.quote.dto.CreateQuoteRequest;
import com.converter.quote.dto.QuoteResponse;
import com.converter.quote.service.QuoteService;
import com.converter.security.AuthenticatedUser;
import com.converter.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Cycle de vie d'un devis client.
 *
 * <p>Un client ne peut consulter ou faire transiter que ses propres
 * devis : {@link com.converter.security.OwnershipService} renvoie
 * {@code 404} (jamais {@code 403}) sur une tentative d'acces au devis
 * d'un autre utilisateur. Ce chemin ne correspond a aucun motif public
 * ni a {@code /api/admin/**} dans {@code SecurityConfig} : il exige
 * donc deja une authentification via la regle generale
 * {@code anyRequest().authenticated()}, sans qu'aucune regle
 * supplementaire n'ait ete necessaire.
 */
@RestController
@RequestMapping("/api/v1/quotes")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Devis", description = "Simulation et confirmation d'un devis XOF/CNY")
public class QuoteController {

    private final QuoteService quoteService;

    public QuoteController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @PostMapping
    @Operation(summary = "Creer un devis",
            description = "Le client fournit sa seule intention (SEND_XOF + amountXof, ou "
                    + "RECEIVE_CNY + amountCny) : le taux, la marge, les frais et le montant "
                    + "final sont exclusivement calcules par le backend. Le devis est valide "
                    + "30 minutes et son snapshot financier est immuable des sa creation.")
    public ResponseEntity<ApiResponse<QuoteResponse>> create(
            @Valid @RequestBody CreateQuoteRequest request,
            @AuthenticatedUser CurrentUser currentUser) {
        QuoteResponse response = quoteService.create(request, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response, "Devis cree."));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulter un devis",
            description = "Renvoie 404 si le devis n'existe pas ou n'appartient pas au client authentifie.")
    public ResponseEntity<ApiResponse<QuoteResponse>> get(
            @PathVariable UUID id,
            @AuthenticatedUser CurrentUser currentUser) {
        return ResponseEntity.ok(ApiResponse.of(quoteService.get(id, currentUser.getId())));
    }

    @PostMapping("/{id}/accept")
    @Operation(summary = "Accepter un devis",
            description = "Transition ACTIVE -> ACCEPTED. Echoue avec 409 QUOTE_EXPIRED si les "
                    + "30 minutes sont ecoulees, ou 409 INVALID_QUOTE_STATE si le devis a deja "
                    + "ete accepte ou annule.")
    public ResponseEntity<ApiResponse<QuoteResponse>> accept(
            @PathVariable UUID id,
            @AuthenticatedUser CurrentUser currentUser) {
        return ResponseEntity.ok(ApiResponse.of(quoteService.accept(id, currentUser.getId()), "Devis accepte."));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Annuler un devis",
            description = "Transition ACTIVE -> CANCELLED.")
    public ResponseEntity<ApiResponse<QuoteResponse>> cancel(
            @PathVariable UUID id,
            @AuthenticatedUser CurrentUser currentUser) {
        return ResponseEntity.ok(ApiResponse.of(quoteService.cancel(id, currentUser.getId()), "Devis annule."));
    }
}
