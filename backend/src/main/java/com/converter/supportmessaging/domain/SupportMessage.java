package com.converter.supportmessaging.domain;

import com.converter.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/** Un message du fil SAV — jamais modifie ni supprime apres envoi ({@code @Immutable}). */
@Entity
@Table(name = "support_messages")
@Immutable
public class SupportMessage extends BaseEntity {

    @Column(name = "thread_id", nullable = false)
    private UUID threadId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_role", nullable = false, length = 16)
    private SenderRole senderRole;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(name = "body", nullable = false, length = 2000)
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SupportMessage() {
        // Requis par JPA.
    }

    public SupportMessage(UUID threadId, SenderRole senderRole, UUID senderId, String body, Instant createdAt) {
        this.threadId = threadId;
        this.senderRole = senderRole;
        this.senderId = senderId;
        this.body = body;
        this.createdAt = createdAt;
    }

    public UUID getThreadId() {
        return threadId;
    }

    public SenderRole getSenderRole() {
        return senderRole;
    }

    public UUID getSenderId() {
        return senderId;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
