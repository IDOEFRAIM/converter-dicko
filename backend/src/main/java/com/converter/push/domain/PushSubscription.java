package com.converter.push.domain;

import com.converter.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Abonnement Web Push d'un navigateur (PWA, mission "blocages Apple/Meta" oct. 2026). Un meme
 * utilisateur peut avoir plusieurs abonnements actifs (plusieurs appareils/navigateurs) : voir
 * {@code uq_push_subscriptions_endpoint} (V43), unique sur {@code endpoint}, jamais sur
 * {@code userId}.
 *
 * <p>Immuable une fois cree : un navigateur qui change de cles (rotation rare) se desabonne puis
 * se reabonne, ce qui cree une nouvelle ligne (nouvel {@code endpoint}) plutot que de muter
 * celle-ci.
 */
@Entity
@Table(name = "push_subscriptions")
public class PushSubscription extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "endpoint", nullable = false, length = 500)
    private String endpoint;

    @Column(name = "p256dh_key", nullable = false)
    private String p256dhKey;

    @Column(name = "auth_key", nullable = false)
    private String authKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PushSubscription() {
        // Requis par JPA.
    }

    public PushSubscription(UUID userId, String endpoint, String p256dhKey, String authKey, Instant createdAt) {
        this.userId = userId;
        this.endpoint = endpoint;
        this.p256dhKey = p256dhKey;
        this.authKey = authKey;
        this.createdAt = createdAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getP256dhKey() {
        return p256dhKey;
    }

    public String getAuthKey() {
        return authKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
