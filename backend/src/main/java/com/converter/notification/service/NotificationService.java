package com.converter.notification.service;

import com.converter.common.api.PageResponse;
import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.notification.domain.Notification;
import com.converter.notification.domain.NotificationType;
import com.converter.notification.dto.NotificationResponse;
import com.converter.notification.repository.NotificationRepository;
import com.converter.security.OwnershipService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Point d'entree unique de creation et de lecture des notifications
 * internes. MVP : canal {@code IN_APP} uniquement, persistees avant
 * toute consultation -- aucun envoi SMS/WhatsApp/email/push externe.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final OwnershipService ownershipService;
    private final Clock clock;

    public NotificationService(NotificationRepository notificationRepository,
                               OwnershipService ownershipService,
                               Clock clock) {
        this.notificationRepository = notificationRepository;
        this.ownershipService = ownershipService;
        this.clock = clock;
    }

    /**
     * Cree et persiste une notification. Appele par les autres modules (Quote, Payment,
     * PreferredRate, Exchange) depuis leur propre transaction metier.
     *
     * <p>Une notification est un effet de bord, jamais une condition de succes de l'operation
     * financiere qui la declenche : {@code REQUIRES_NEW} l'isole dans sa propre transaction
     * (meme principe que {@link com.converter.audit.service.AuditService}), et tout echec
     * d'ecriture est absorbe (journalise) plutot que propage — un incident sur la table
     * {@code notifications} ne doit jamais faire echouer (ni rollback) un devis, un paiement ou
     * un declenchement de taux preferentiel deja valides.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Notification create(UUID userId, NotificationType type, String title, String message) {
        try {
            Notification notification = new Notification(userId, type, title, message, clock.instant());
            return notificationRepository.save(notification);
        } catch (RuntimeException ex) {
            log.error("Echec d'ecriture de la notification {} pour l'utilisateur {}", type, userId, ex);
            return null;
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> listMine(UUID userId, Pageable pageable) {
        return PageResponse.from(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable),
                NotificationService::toResponse);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public NotificationResponse markRead(UUID id, UUID userId) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> notFound(id));
        ownershipService.assertOwnedBy(notification.getUserId(), userId, ErrorCode.NOTIFICATION_NOT_FOUND,
                "Notification introuvable : " + id);
        notification.markRead(clock.instant());
        return toResponse(notification);
    }

    private BusinessException notFound(UUID id) {
        return new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND, "Notification introuvable : " + id);
    }

    private static NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getType(), notification.getTitle(),
                notification.getMessage(), notification.getCreatedAt(), notification.getReadAt());
    }
}
