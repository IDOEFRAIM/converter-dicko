package com.converter.supportmessaging.service;

import com.converter.common.api.PageResponse;
import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.notification.domain.NotificationType;
import com.converter.notification.service.NotificationService;
import com.converter.supportmessaging.domain.SenderRole;
import com.converter.supportmessaging.domain.SupportMessage;
import com.converter.supportmessaging.domain.SupportThread;
import com.converter.supportmessaging.dto.SupportMessageResponse;
import com.converter.supportmessaging.dto.SupportThreadResponse;
import com.converter.supportmessaging.dto.SupportThreadSummaryResponse;
import com.converter.supportmessaging.repository.SupportMessageRepository;
import com.converter.supportmessaging.repository.SupportThreadRepository;
import com.converter.user.domain.User;
import com.converter.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Messagerie SAV (retour client) : un seul fil de discussion continu par utilisateur -- pas un
 * ticket par reclamation -- ou l'utilisateur peut ecrire librement et n'importe quel
 * administrateur repond dans ce meme fil. Le badge "non lu" de chaque cote decoule simplement de
 * la derniere consultation ({@code userLastReadAt}/{@code adminLastReadAt}), jamais un endpoint
 * "marquer comme lu" separe : ouvrir le fil marque deja tout comme lu pour ce viewer.
 */
@Service
public class SupportService {

    private static final Logger log = LoggerFactory.getLogger(SupportService.class);

    private final SupportThreadRepository threadRepository;
    private final SupportMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    public SupportService(SupportThreadRepository threadRepository,
                          SupportMessageRepository messageRepository,
                          UserRepository userRepository,
                          NotificationService notificationService,
                          Clock clock) {
        this.threadRepository = threadRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    // -----------------------------------------------------------------
    // Client
    // -----------------------------------------------------------------

    @Transactional
    public SupportThreadResponse getOrCreateMyThread(UUID userId) {
        SupportThread thread = getOrCreateThread(userId);
        Instant now = clock.instant();
        thread.markReadByUser(now);
        threadRepository.save(thread);
        return toThreadResponse(thread);
    }

    @Transactional
    public SupportMessageResponse sendAsUser(UUID userId, String body) {
        SupportThread thread = getOrCreateThreadForUpdate(userId);
        SupportMessage saved = appendMessage(thread, SenderRole.USER, userId, body);
        thread.markReadByUser(saved.getCreatedAt());
        threadRepository.save(thread);
        log.info("Message SAV envoye par l'utilisateur {} (fil {})", userId, thread.getId());
        return toMessageResponse(saved);
    }

    // -----------------------------------------------------------------
    // Admin
    // -----------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<SupportThreadSummaryResponse> listThreadsForAdmin(Pageable pageable) {
        Page<SupportThread> page = threadRepository.findAllByOrderByUpdatedAtDesc(pageable);
        return PageResponse.from(page, this::toSummary);
    }

    @Transactional
    public SupportThreadResponse getThreadForAdmin(UUID userId) {
        SupportThread thread = threadRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Aucun fil de messagerie pour cet utilisateur : " + userId));
        Instant now = clock.instant();
        thread.markReadByAdmin(now);
        threadRepository.save(thread);
        return toThreadResponse(thread);
    }

    @Transactional
    public SupportMessageResponse sendAsAdmin(UUID userId, String body, UUID adminId) {
        SupportThread thread = getOrCreateThreadForUpdate(userId);
        SupportMessage saved = appendMessage(thread, SenderRole.ADMIN, adminId, body);
        thread.markReadByAdmin(saved.getCreatedAt());
        threadRepository.save(thread);

        notificationService.create(userId, NotificationType.SUPPORT_REPLY, "Nouvelle reponse du support",
                "Le support a repondu a votre message.");
        log.info("Message SAV envoye par l'administrateur {} au fil de {}", adminId, userId);
        return toMessageResponse(saved);
    }

    // -----------------------------------------------------------------

    private SupportMessage appendMessage(SupportThread thread, SenderRole senderRole, UUID senderId, String body) {
        Instant now = clock.instant();
        SupportMessage message = new SupportMessage(thread.getId(), senderRole, senderId, body.trim(), now);
        SupportMessage saved = messageRepository.save(message);
        thread.touch(now);
        return saved;
    }

    /** Lecture seule (consultation du fil) : cree le fil s'il n'existe pas encore, sans verrou. */
    private SupportThread getOrCreateThread(UUID userId) {
        return threadRepository.findByUserId(userId).orElseGet(() -> createThread(userId));
    }

    /** Ecriture (envoi d'un message) : verrou pessimiste sur le fil existant s'il y en a un. */
    private SupportThread getOrCreateThreadForUpdate(UUID userId) {
        return threadRepository.findByUserIdForUpdate(userId).orElseGet(() -> createThread(userId));
    }

    private SupportThread createThread(UUID userId) {
        try {
            return threadRepository.saveAndFlush(new SupportThread(userId, clock.instant()));
        } catch (DataIntegrityViolationException ex) {
            // Course concurrente sur le tout premier message de ce fil (uq_support_threads_user) :
            // l'autre a gagne, on relit simplement son fil deja cree.
            return threadRepository.findByUserId(userId).orElseThrow(() -> ex);
        }
    }

    private SupportThreadResponse toThreadResponse(SupportThread thread) {
        List<SupportMessageResponse> messages = messageRepository.findByThreadIdOrderByCreatedAtAsc(thread.getId())
                .stream().map(this::toMessageResponse).toList();
        return new SupportThreadResponse(thread.getUserId(), messages);
    }

    private SupportMessageResponse toMessageResponse(SupportMessage message) {
        return new SupportMessageResponse(message.getId(), message.getSenderRole() == SenderRole.ADMIN,
                message.getBody(), message.getCreatedAt());
    }

    private SupportThreadSummaryResponse toSummary(SupportThread thread) {
        User user = userRepository.findById(thread.getUserId()).orElse(null);
        SupportMessage last = messageRepository.findFirstByThreadIdOrderByCreatedAtDesc(thread.getId()).orElse(null);
        Instant sinceAdminRead = thread.getAdminLastReadAt() != null ? thread.getAdminLastReadAt() : Instant.EPOCH;
        boolean hasUnread = messageRepository.existsByThreadIdAndSenderRoleAndCreatedAtAfter(
                thread.getId(), SenderRole.USER, sinceAdminRead);
        return new SupportThreadSummaryResponse(
                thread.getUserId(),
                user == null ? "Utilisateur supprime" : user.fullName(),
                user == null ? null : user.getPhone(),
                last == null ? null : last.getBody(),
                last != null && last.getSenderRole() == SenderRole.ADMIN,
                last == null ? thread.getCreatedAt() : last.getCreatedAt(),
                hasUnread);
    }
}
