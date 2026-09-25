package com.converter.transfi.domain;

import com.converter.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Trace d'un webhook TransFi deja traite — idempotence (voir {@code docs/TRANSFI_INTEGRATION.md}
 * section 4 : "un meme webhook peut arriver plusieurs fois"). La contrainte unique sur
 * {@code providerEventId} (V44) est la SEULE protection reelle : une livraison en double echoue
 * ici avec une violation de contrainte, rattrapee par {@code TransfiWebhookController} qui repond
 * alors 200 sans rejouer aucun effet metier.
 */
@Entity
@Table(name = "transfi_webhook_events")
public class TransfiWebhookEvent extends BaseEntity {

    @Column(name = "provider_event_id", nullable = false, length = 150)
    private String providerEventId;

    @Column(name = "event_type", length = 64)
    private String eventType;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    protected TransfiWebhookEvent() {
        // Requis par JPA.
    }

    public TransfiWebhookEvent(String providerEventId, String eventType, Instant receivedAt) {
        this.providerEventId = providerEventId;
        this.eventType = eventType;
        this.receivedAt = receivedAt;
    }

    public String getProviderEventId() {
        return providerEventId;
    }

    public String getEventType() {
        return eventType;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
