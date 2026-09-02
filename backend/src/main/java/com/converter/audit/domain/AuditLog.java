package com.converter.audit.domain;

import com.converter.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Entree immuable du journal d'audit.
 *
 * <p>Deux choix de conception meritent une explication.
 *
 * <p><b>Aucune cle etrangere vers {@code users}.</b> Le journal doit
 * survivre a la disparition de l'acteur, et une contrainte
 * referentielle empecherait toute suppression de compte. Le numero est
 * donc recopie dans {@code actorPhone} au moment de l'ecriture : c'est
 * une photographie de l'identite, pas un lien vivant.
 *
 * <p><b>Aucun horodatage gere par JPA auditing.</b> {@code createdAt}
 * est fixe explicitement par le service, car une entree d'audit est
 * ecrite dans une transaction separee dont l'instant doit correspondre
 * a celui de l'action, pas a celui du flush.
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog extends BaseEntity {

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_phone", length = 20)
    private String actorPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 48)
    private AuditAction action;

    @Column(name = "entity_type", length = 48)
    private String entityType;

    @Column(name = "entity_id", length = 64)
    private String entityId;

    /**
     * Contexte libre serialise en JSON (valeurs avant/apres, montants,
     * motifs). Stocke en {@code jsonb} : PostgreSQL peut alors indexer
     * et interroger le contenu si une enquete l'exige.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata")
    private String metadata;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLog() {
        // Requis par JPA.
    }

    public AuditLog(UUID actorId,
                    String actorPhone,
                    AuditAction action,
                    String entityType,
                    String entityId,
                    String metadata,
                    String ipAddress,
                    String userAgent,
                    Instant createdAt) {
        this.actorId = actorId;
        this.actorPhone = actorPhone;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.metadata = metadata;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.createdAt = createdAt;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getActorPhone() {
        return actorPhone;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getMetadata() {
        return metadata;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
