package com.converter.transfi.web;

import com.converter.config.props.TransFiProperties;
import com.converter.transfi.service.TransfiOrchestrationService;
import com.converter.transfi.util.WebhookSignatureVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Hidden;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Point d'entree des notifications TransFi (voir {@code docs/TRANSFI_INTEGRATION.md}). Route
 * publique (voir {@code SecurityConfig}, aucun jeton porte possible) : l'authentification se fait
 * ICI, par verification de signature HMAC, jamais par Spring Security.
 *
 * <p><b>En-tete de signature et forme du payload PROVISOIRES</b> (voir
 * {@link WebhookSignatureVerifier} et les noms de champ ci-dessous, repris tels quels du brief
 * transmis) — a confirmer contre la documentation officielle TransFi avant activation en
 * production.
 *
 * <p>Repond TOUJOURS 200 une fois la signature validee (meme si le {@code providerOrderId} est
 * inconnu ou le traitement metier degrade) : un code d'erreur ferait retenter TransFi
 * indefiniment un webhook que nous avons deja recu et journalise, ce qui n'apporterait rien.
 * Seule une signature invalide ou une integration desactivee renvoie une erreur.
 */
@RestController
@RequestMapping("/api/webhooks/transfi")
@Hidden
public class TransfiWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TransfiWebhookController.class);

    private final TransFiProperties properties;
    private final TransfiOrchestrationService orchestrationService;
    private final ObjectMapper objectMapper;

    public TransfiWebhookController(TransFiProperties properties, TransfiOrchestrationService orchestrationService,
                                    ObjectMapper objectMapper) {
        this.properties = properties;
        this.orchestrationService = orchestrationService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody byte[] rawBody,
                                        @RequestHeader(value = "X-Transfi-Signature", required = false) String signature) {
        if (!properties.webhookConfigured()) {
            log.warn("Webhook TransFi recu alors que app.transfi.webhook-secret n'est pas configure -- rejete.");
            return ResponseEntity.status(503).build();
        }
        if (!WebhookSignatureVerifier.isValid(rawBody, signature, properties.webhookSecret())) {
            log.warn("Webhook TransFi recu avec une signature invalide ou absente -- rejete.");
            return ResponseEntity.status(401).build();
        }

        String payload = new String(rawBody, java.nio.charset.StandardCharsets.UTF_8);
        try {
            JsonNode root = objectMapper.readTree(payload);
            // PROVISOIRE -- noms de champ ("eventId"/"direction"/"orderId"/"status") a confirmer
            // contre la documentation officielle TransFi, voir Javadoc de classe.
            String eventId = textOrNull(root, "eventId");
            String direction = textOrNull(root, "direction");
            String providerOrderId = textOrNull(root, "orderId");
            String status = textOrNull(root, "status");
            orchestrationService.handleWebhook(eventId, direction, providerOrderId, status, payload);
        } catch (Exception e) {
            // Jamais laisser une erreur de traitement redemander un renvoi infini a TransFi -- un
            // webhook illisible ou inattendu est journalise en erreur pour investigation manuelle,
            // pas rejoue automatiquement (voir Javadoc de classe).
            log.error("Erreur de traitement d'un webhook TransFi -- payload journalise pour investigation manuelle.", e);
        }
        return ResponseEntity.ok().build();
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
