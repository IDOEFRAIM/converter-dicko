package com.converter.push.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Miroir exact de l'objet {@code PushSubscription} standard renvoye par
 * {@code PushManager.subscribe()} cote navigateur -- jamais reconstruit differemment. */
@Schema(description = "Abonnement Web Push tel que renvoye par PushManager.subscribe() cote navigateur")
public record SubscribePushRequest(

        @NotBlank(message = "L'endpoint est obligatoire")
        String endpoint,

        @NotNull(message = "Les cles de l'abonnement sont obligatoires")
        @Valid
        Keys keys
) {
    public record Keys(

            @NotBlank(message = "La cle p256dh est obligatoire")
            String p256dh,

            @NotBlank(message = "La cle auth est obligatoire")
            String auth
    ) {
    }
}
