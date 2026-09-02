package com.converter.common.idempotency;

import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Point d'entree unique de l'idempotence HTTP, appele explicitement depuis les controleurs des
 * operations financierement sensibles (creation d'ordre, soumission de paiement, execution d'un
 * reglement, operations de tresorerie — voir {@code docs/AUDIT_BUSINESS_LOGIC.md} §17).
 *
 * <p>L'en-tete {@code Idempotency-Key} reste <b>optionnel</b> : son absence preserve exactement
 * le comportement anterieur (aucune protection au-dela des contraintes SQL deja en place). Quand
 * il est fourni :
 * <ul>
 *   <li>premiere requete avec cette cle → l'action est executee, sa reponse memorisee ;</li>
 *   <li>rejeu avec la meme cle et le meme corps de requete → la reponse d'origine est rejouee
 *       <b>sans jamais ré-executer l'action</b> (jamais de double ordre, double paiement...) ;</li>
 *   <li>meme cle, corps de requete different → {@code 409 IDEMPOTENCY_KEY_REUSED} ;</li>
 *   <li>meme cle, requete encore en cours de traitement (course ou crash en vol) →
 *       {@code 409 IDEMPOTENT_REQUEST_IN_PROGRESS}.</li>
 * </ul>
 *
 * <p>La cle de recherche inclut toujours {@code userId} extrait du principal authentifie —
 * jamais un identifiant fourni par le client — un utilisateur ne peut donc jamais lire ni
 * declencher le rejeu de la reponse mise en cache d'un autre utilisateur.
 */
@Component
public class IdempotencyGuard {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyGuard.class);
    private static final int MAX_KEY_LENGTH = 80;

    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public IdempotencyGuard(IdempotencyService idempotencyService, ObjectMapper objectMapper) {
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    /**
     * Execute {@code action} au plus une fois par {@code (userId, endpoint, idemKey)}.
     *
     * @param idemKey      valeur de l'en-tete {@code Idempotency-Key} ; {@code null}/vide =
     *                     aucune protection appliquee, {@code action} est simplement executee
     * @param requestBody  corps de la requete deja deserialise, utilise pour calculer
     *                     l'empreinte SHA-256 qui detecte une reutilisation de cle avec un
     *                     corps different
     * @param responseType type generique de la reponse (ex. {@code ApiResponse<OrderDetailResponse>}),
     *                     necessaire pour desserialiser fidelement une reponse rejouee
     *
     * <p>{@code @Transactional} : l'action metier ({@code REQUIRED}) et
     * {@link IdempotencyService#complete} ({@code REQUIRED}) rejoignent cette transaction, donc
     * l'effet metier et l'enregistrement de la reponse committent ensemble ou pas du tout
     * (passe 2, P2-3). {@link IdempotencyService#tryInsert}/{@code findExisting}/
     * {@code releasePending} restent {@code REQUIRES_NEW} et committent independamment.
     */
    @Transactional
    public <T> ResponseEntity<T> guard(UUID userId, String endpoint, String idemKey, Object requestBody,
                                       TypeReference<T> responseType, Supplier<ResponseEntity<T>> action) {
        if (idemKey == null || idemKey.isBlank()) {
            return action.get();
        }
        if (idemKey.length() > MAX_KEY_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Idempotency-Key ne doit pas depasser " + MAX_KEY_LENGTH + " caracteres.");
        }

        String requestHash = sha256(writeJson(requestBody));
        Optional<IdempotencyKey> existing = claimOrLookupExisting(userId, endpoint, idemKey, requestHash);
        if (existing.isPresent()) {
            IdempotencyKey key = existing.get();
            if (!key.getRequestHash().equals(requestHash)) {
                throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                        "La cle d'idempotence '" + idemKey + "' a deja ete utilisee avec un corps de requete different.");
            }
            if (key.isPending()) {
                throw new BusinessException(ErrorCode.IDEMPOTENT_REQUEST_IN_PROGRESS,
                        "Une requete identique (cle '" + idemKey + "') est deja en cours de traitement.");
            }
            log.info("Rejeu idempotent : utilisateur={}, endpoint={}, cle={}, statut={}",
                    userId, endpoint, idemKey, key.getResponseStatus());
            return ResponseEntity.status(key.getResponseStatus()).body(readJson(key.getResponseBody(), responseType));
        }

        try {
            ResponseEntity<T> response = action.get();
            idempotencyService.complete(userId, endpoint, idemKey,
                    response.getStatusCode().value(), writeJson(response.getBody()));
            return response;
        } catch (RuntimeException ex) {
            // Un echec metier (validation, etat invalide, conflit...) ne doit jamais bloquer
            // indefiniment une nouvelle tentative legitime avec la meme cle : la capture "en
            // attente" est retiree, seule une reponse effectivement REUSSIE reste mise en cache.
            idempotencyService.releasePending(userId, endpoint, idemKey);
            throw ex;
        }
    }

    /**
     * Tente la capture ; en cas de collision (cle deja presente), rattrape l'exception ICI —
     * hors de la transaction avortee de {@link IdempotencyService#tryInsert} — puis relit l'etat
     * existant dans une transaction fraiche. Voir la Javadoc de {@code tryInsert} pour le detail
     * du piege PostgreSQL evite par cette sequence en deux temps.
     *
     * @return {@code Optional.empty()} si la capture a reussi (l'appelant doit executer l'action) ;
     *         sinon la ligne existante, a interpreter (rejeu, conflit, ou en cours).
     */
    private Optional<IdempotencyKey> claimOrLookupExisting(UUID userId, String endpoint, String idemKey, String requestHash) {
        try {
            idempotencyService.tryInsert(userId, endpoint, idemKey, requestHash);
            return Optional.empty();
        } catch (DataIntegrityViolationException duplicate) {
            return Optional.of(idempotencyService.findExisting(userId, endpoint, idemKey).orElseThrow(() -> duplicate));
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Echec de serialisation JSON pour le calcul d'idempotence.", ex);
        }
    }

    private <T> T readJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception ex) {
            throw new IllegalStateException("Echec de deserialisation d'une reponse idempotente memorisee.", ex);
        }
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponible sur cette JVM", ex);
        }
    }
}
