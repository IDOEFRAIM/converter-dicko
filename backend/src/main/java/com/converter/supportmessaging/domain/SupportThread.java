package com.converter.supportmessaging.domain;

import com.converter.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Fil de messagerie SAV — UN SEUL par utilisateur ({@code uq_support_threads_user}), comme une
 * conversation de support continue plutot qu'un ticket par reclamation : l'utilisateur ecrit,
 * n'importe quel administrateur peut repondre dans ce meme fil.
 *
 * <p>{@code userLastReadAt}/{@code adminLastReadAt} pilotent le badge "non lu" de chaque cote —
 * mis a jour des que le viewer concerne consulte le fil (voir {@code SupportService}), jamais un
 * endpoint "marquer comme lu" separe.
 */
@Entity
@Table(name = "support_threads")
public class SupportThread extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "user_last_read_at")
    private Instant userLastReadAt;

    @Column(name = "admin_last_read_at")
    private Instant adminLastReadAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SupportThread() {
        // Requis par JPA.
    }

    public SupportThread(UUID userId, Instant now) {
        this.userId = userId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void touch(Instant now) {
        this.updatedAt = now;
    }

    public void markReadByUser(Instant now) {
        this.userLastReadAt = now;
    }

    public void markReadByAdmin(Instant now) {
        this.adminLastReadAt = now;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getUserLastReadAt() {
        return userLastReadAt;
    }

    public Instant getAdminLastReadAt() {
        return adminLastReadAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
