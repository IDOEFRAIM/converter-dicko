package com.converter.push.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** {@code available = false} : les notifications push ne sont pas configurees sur ce serveur --
 * le frontend masque alors toute UI d'activation plutot que d'exposer un bouton qui echouerait. */
@Schema(description = "Disponibilite et cle publique VAPID des notifications push")
public record PushConfigResponse(
        boolean available,
        @Schema(description = "Cle publique VAPID (base64url) -- jamais un secret, cf. RFC 8292")
        String vapidPublicKey
) {
}
