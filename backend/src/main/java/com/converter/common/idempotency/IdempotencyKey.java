package com.converter.common.idempotency;

import com.converter.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Capture d'une cle {@code Idempotency-Key} soumise par un client, pour un utilisateur et un
 * endpoint donnes (table {@code idempotency_keys}, migration {@code V1}).
 *
 * <p>Cycle de vie en deux temps, jamais trois :
 * <ol>
 *   <li><b>Capture</b> ({@code responseStatus == null}) : la ligne existe, l'operation
 *       correspondante est en cours d'execution (ou a echoue et n'a pas encore ete nettoyee) ;</li>
 *   <li><b>Completee</b> ({@code responseStatus != null}) : l'operation a reussi, sa reponse est
 *       memorisee et sera rejouee telle quelle pour toute requete ulterieure portant la meme cle
 *       et le meme corps de requete.</li>
 * </ol>
 *
 * <p>Aucune ligne "completee" n'est jamais modifiee — seule {@link #complete} existe comme
 * transition, appelee une seule fois par {@link IdempotencyService}.
 */
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey extends BaseEntity {

    @Column(name = "idem_key", nullable = false, length = 80)
    private String idemKey;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "endpoint", nullable = false, length = 120)
    private String endpoint;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "response_status")
    private Integer responseStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected IdempotencyKey() {
        // Requis par JPA.
    }

    public IdempotencyKey(String idemKey, UUID userId, String endpoint, String requestHash,
                          Instant createdAt, Instant expiresAt) {
        this.idemKey = idemKey;
        this.userId = userId;
        this.endpoint = endpoint;
        this.requestHash = requestHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    /** Seule transition : memorise le resultat de l'operation deja executee. */
    public void complete(int status, String responseBodyJson) {
        this.responseStatus = status;
        this.responseBody = responseBodyJson;
    }

    public boolean isPending() {
        return responseStatus == null;
    }

    public String getIdemKey() {
        return idemKey;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
