package com.converter.transfi.client;

import com.converter.config.props.TransFiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Client HTTP reel de l'API TransFi BizPay, authentifie en HTTP Basic (identifiant client + cle
 * secrete, voir {@link TransFiProperties}) — jamais construit tant que
 * {@link TransFiProperties#configured()} est faux (voir {@link #ensureConfigured()}).
 *
 * <p><b>Chemins d'endpoint PROVISOIRES</b> ({@code POST /v3/orders}, {@code GET /v3/orders/{id}})
 * et forme de payload PROVISOIRE (champs {@code type}/{@code amount}/{@code currency}/
 * {@code reference}/{@code beneficiary}) — repris tels quels du brief transmis (voir
 * {@code docs/TRANSFI_INTEGRATION.md}), <b>a verifier ligne a ligne contre la documentation
 * officielle TransFi (ou une collection Postman fournie par TransFi) avant toute activation en
 * production</b>. Rien ici ne doit etre considere comme un contrat confirme.
 *
 * <p>Les identifiants ({@code clientId}/{@code clientSecret}) ne sont JAMAIS journalises, y
 * compris en cas d'erreur (voir {@link #logSafely}) — seul le statut HTTP et un extrait du corps
 * de reponse le sont, jamais l'en-tete {@code Authorization} envoye.
 */
@Component
public class TransFiHttpClient implements TransFiClient {

    private static final Logger log = LoggerFactory.getLogger(TransFiHttpClient.class);
    private static final String ORDERS_PATH = "/v3/orders"; // PROVISOIRE -- voir Javadoc de classe.

    private final TransFiProperties properties;
    private final ObjectMapper objectMapper;
    private volatile RestClient restClient;

    public TransFiHttpClient(TransFiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public TransFiOrderResult createOrder(TransFiCreateOrderRequest request) {
        ensureConfigured();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("type", request.type().name()); // PROVISOIRE -- voir Javadoc de classe.
        body.put("amount", request.amount().toPlainString());
        body.put("currency", request.currency());
        body.put("reference", request.customerReference());
        if (request.type() == TransFiOrderType.PAYOUT) {
            ObjectNode beneficiary = body.putObject("beneficiary");
            beneficiary.put("name", request.beneficiaryName());
            beneficiary.put("account", request.beneficiaryAccount());
        }
        JsonNode response = post(ORDERS_PATH, body);
        return toResult(response);
    }

    @Override
    public TransFiOrderResult getOrder(String providerOrderId) {
        ensureConfigured();
        JsonNode response = get(ORDERS_PATH + "/" + providerOrderId);
        return toResult(response);
    }

    private JsonNode post(String path, ObjectNode body) {
        try {
            String raw = client().post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(raw == null ? "{}" : raw);
        } catch (RestClientException e) {
            logSafely("POST", path, e);
            throw new TransFiApiException("Appel TransFi echoue (" + path + ")", e);
        } catch (Exception e) {
            throw new TransFiApiException("Reponse TransFi illisible (" + path + ")", e);
        }
    }

    private JsonNode get(String path) {
        try {
            String raw = client().get().uri(path).retrieve().body(String.class);
            return objectMapper.readTree(raw == null ? "{}" : raw);
        } catch (RestClientException e) {
            logSafely("GET", path, e);
            throw new TransFiApiException("Appel TransFi echoue (" + path + ")", e);
        } catch (Exception e) {
            throw new TransFiApiException("Reponse TransFi illisible (" + path + ")", e);
        }
    }

    // PROVISOIRE -- voir Javadoc de classe : noms de champs de reponse ("id"/"status"/"payUrl")
    // a confirmer contre la documentation officielle.
    private TransFiOrderResult toResult(JsonNode response) {
        String id = textOrNull(response, "id");
        String status = textOrNull(response, "status");
        String payUrl = textOrNull(response, "payUrl");
        return new TransFiOrderResult(id, status, payUrl, response.toString());
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private void ensureConfigured() {
        if (!properties.configured()) {
            throw new IllegalStateException(
                    "TransFiHttpClient appele sans identifiants configures (app.transfi.*) -- "
                            + "verifier TransFiProperties.configured() avant tout appel.");
        }
    }

    /** Construit paresseusement (identifiants absents au demarrage = pas d'erreur au boot). */
    private RestClient client() {
        RestClient existing = restClient;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (restClient == null) {
                SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
                requestFactory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
                requestFactory.setReadTimeout((int) Duration.ofSeconds(30).toMillis());
                String basicAuth = Base64.getEncoder().encodeToString(
                        (properties.clientId() + ":" + properties.clientSecret()).getBytes(StandardCharsets.UTF_8));
                restClient = RestClient.builder()
                        .baseUrl(properties.baseUrl())
                        .requestFactory(requestFactory)
                        .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth)
                        .build();
            }
            return restClient;
        }
    }

    private void logSafely(String method, String path, Exception e) {
        // Jamais l'en-tete Authorization ni les identifiants : seuls la methode, le chemin et le
        // type d'exception sont utiles au diagnostic et surs a journaliser.
        log.error("Echec appel TransFi {} {} : {}", method, path, e.getClass().getSimpleName());
    }
}
