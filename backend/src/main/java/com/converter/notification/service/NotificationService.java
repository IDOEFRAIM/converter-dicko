package com.converter.notification.service;

import com.converter.common.api.PageResponse;
import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.notification.domain.Notification;
import com.converter.notification.domain.NotificationType;
import com.converter.notification.dto.NotificationResponse;
import com.converter.notification.repository.NotificationRepository;
import com.converter.push.service.PushNotificationSender;
import com.converter.security.OwnershipService;
import com.converter.user.domain.ExperienceProfile;
import com.converter.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Point d'entree unique de creation et de lecture des notifications internes. Persistees avant
 * toute consultation (canal {@code IN_APP}) ; un envoi Web Push (PWA, mission "blocages Apple/
 * Meta" oct. 2026) est tente en best-effort en plus -- voir {@link PushNotificationSender}, qui
 * n'est lui-meme actif que si des cles VAPID sont configurees sur ce serveur. Aucun SMS/WhatsApp/
 * email n'est integre.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final OwnershipService ownershipService;
    private final UserRepository userRepository;
    private final PushNotificationSender pushNotificationSender;
    private final Clock clock;

    public NotificationService(NotificationRepository notificationRepository,
                               OwnershipService ownershipService,
                               UserRepository userRepository,
                               PushNotificationSender pushNotificationSender,
                               Clock clock) {
        this.notificationRepository = notificationRepository;
        this.ownershipService = ownershipService;
        this.userRepository = userRepository;
        this.pushNotificationSender = pushNotificationSender;
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
     *
     * <p>Le {@code title} fourni par l'appelant est un texte PRO neutre par defaut ; pour un
     * profil STUDENT_MALE/STUDENT_FEMALE et un type de notification celebrable, il est remplace
     * par une variante differenciee via {@link NotificationCopy} (mission "differenciation
     * marketing" section "Notifications"). Le {@code message} n'est jamais modifie : il porte les
     * donnees reelles calculees par le service appelant (montants, references...).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Notification create(UUID userId, NotificationType type, String title, String message) {
        try {
            ExperienceProfile profile = userRepository.findById(userId)
                    .map(user -> user.getExperienceProfile())
                    .orElse(ExperienceProfile.PRO);
            String personalizedTitle = NotificationCopy.title(type, profile, title);
            Notification notification = new Notification(userId, type, personalizedTitle, message, clock.instant());
            Notification saved = notificationRepository.save(notification);
            pushNotificationSender.sendToUser(userId, personalizedTitle, message);
            return saved;
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
