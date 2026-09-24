package com.converter.push.web;

import com.converter.common.api.ApiResponse;
import com.converter.config.props.PushProperties;
import com.converter.push.dto.PushConfigResponse;
import com.converter.push.dto.SubscribePushRequest;
import com.converter.push.dto.UnsubscribePushRequest;
import com.converter.push.service.PushSubscriptionService;
import com.converter.security.AuthenticatedUser;
import com.converter.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Notifications Web Push (PWA, mission "blocages Apple/Meta" oct. 2026). L'envoi effectif est
 * declenche depuis {@code NotificationService.create}, jamais depuis ce controleur -- il ne gere
 * que le cycle de vie des abonnements.
 */
@RestController
@RequestMapping("/api/v1/push")
@Tag(name = "Notifications push", description = "Abonnement Web Push (PWA)")
public class PushController {

    private final PushProperties properties;
    private final PushSubscriptionService subscriptionService;

    public PushController(PushProperties properties, PushSubscriptionService subscriptionService) {
        this.properties = properties;
        this.subscriptionService = subscriptionService;
    }

    @GetMapping("/config")
    @Operation(summary = "Disponibilite et cle publique VAPID",
            description = "Public (pas de jeton requis) : la cle publique VAPID n'est pas un secret, et le "
                    + "frontend doit pouvoir savoir si le push est disponible avant meme la connexion.")
    public ResponseEntity<ApiResponse<PushConfigResponse>> config() {
        PushConfigResponse response = new PushConfigResponse(properties.configured(), properties.publicKey());
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @PostMapping("/subscriptions")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "S'abonner aux notifications push",
            description = "Idempotent : resouscrire avec le meme endpoint remplace l'abonnement existant.")
    public ResponseEntity<ApiResponse<Void>> subscribe(
            @Valid @RequestBody SubscribePushRequest request,
            @AuthenticatedUser CurrentUser currentUser) {
        subscriptionService.subscribe(currentUser.getId(), request);
        return ResponseEntity.ok(ApiResponse.message("Abonnement enregistre."));
    }

    @DeleteMapping("/subscriptions")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "Se desabonner des notifications push")
    public ResponseEntity<ApiResponse<Void>> unsubscribe(@Valid @RequestBody UnsubscribePushRequest request) {
        subscriptionService.unsubscribe(request.endpoint());
        return ResponseEntity.ok(ApiResponse.message("Abonnement supprime."));
    }
}
