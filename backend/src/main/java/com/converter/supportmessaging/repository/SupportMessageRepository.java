package com.converter.supportmessaging.repository;

import com.converter.supportmessaging.domain.SenderRole;
import com.converter.supportmessaging.domain.SupportMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, UUID> {

    List<SupportMessage> findByThreadIdOrderByCreatedAtAsc(UUID threadId);

    Optional<SupportMessage> findFirstByThreadIdOrderByCreatedAtDesc(UUID threadId);

    /** Alimente le badge "non lu" cote admin (voir {@code SupportService#toSummary}). */
    boolean existsByThreadIdAndSenderRoleAndCreatedAtAfter(UUID threadId, SenderRole senderRole, Instant after);
}
