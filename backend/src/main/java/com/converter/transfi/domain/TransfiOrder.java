package com.converter.transfi.domain;

import com.converter.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Suivi d'un ordre payin/payout cote TransFi, rattache a notre {@link com.converter.order.domain.Order}
 * (voir {@code docs/TRANSFI_INTEGRATION.md}). Au plus un {@code PAYIN} et un {@code PAYOUT} par
 * ordre (contrainte {@code uq_transfi_orders_order_direction}, V44) : un ordre n'est jamais routé
 * deux fois vers TransFi dans le meme sens.
 *
 * <p>{@code rawPayload} conserve le dernier payload recu tel quel (creation ou webhook) — utile
 * au rapprochement et au diagnostic tant que le contrat exact de l'API n'est pas totalement
 * stabilise cote nous ; jamais parse a nouveau depuis ce champ par la logique metier, qui doit
 * toujours s'appuyer sur les colonnes typees.
 */
@Entity
@Table(name = "transfi_orders")
public class TransfiOrder extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 16)
    private TransfiOrderDirection direction;

    @Column(name = "provider_order_id", length = 120)
    private String providerOrderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TransfiOrderStatus status;

    /** Lien de paiement renvoye par TransFi pour un PAYIN — a afficher/rediriger le client. */
    @Column(name = "pay_url", length = 500)
    private String payUrl;

    @Column(name = "raw_payload", columnDefinition = "text")
    private String rawPayload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TransfiOrder() {
        // Requis par JPA.
    }

    public TransfiOrder(UUID orderId, TransfiOrderDirection direction, Instant createdAt) {
        this.orderId = orderId;
        this.direction = direction;
        this.status = TransfiOrderStatus.CREATED;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public void applyProviderResult(String providerOrderId, String payUrl, String rawPayload, Instant now) {
        this.providerOrderId = providerOrderId;
        this.payUrl = payUrl;
        this.rawPayload = rawPayload;
        this.updatedAt = now;
    }

    public void applyStatus(TransfiOrderStatus status, String rawPayload, Instant now) {
        this.status = status;
        this.rawPayload = rawPayload;
        this.updatedAt = now;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public TransfiOrderDirection getDirection() {
        return direction;
    }

    public String getProviderOrderId() {
        return providerOrderId;
    }

    public TransfiOrderStatus getStatus() {
        return status;
    }

    public String getPayUrl() {
        return payUrl;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
