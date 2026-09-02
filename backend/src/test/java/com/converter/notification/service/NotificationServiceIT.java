package com.converter.notification.service;

import com.converter.common.exception.BusinessException;
import com.converter.notification.domain.Notification;
import com.converter.notification.domain.NotificationType;
import com.converter.notification.dto.NotificationResponse;
import com.converter.notification.repository.NotificationRepository;
import com.converter.support.AbstractRateQuoteIT;
import com.converter.user.domain.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifie {@link NotificationService} : creation, lecture, compteur non lu, ownership. */
class NotificationServiceIT extends AbstractRateQuoteIT {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void create_persistsUnreadNotification() {
        UUID userId = createUser(RoleCode.USER).getId();

        Notification notification = notificationService.create(userId, NotificationType.QUOTE_CREATED, "Titre", "Message");

        assertThat(notification.getId()).isNotNull();
        assertThat(notification.getReadAt()).isNull();
    }

    @Test
    void unreadCount_reflectsOnlyUnreadNotifications() {
        UUID userId = createUser(RoleCode.USER).getId();
        long before = notificationService.unreadCount(userId);
        Notification n1 = notificationService.create(userId, NotificationType.QUOTE_CREATED, "A", "A");
        notificationService.create(userId, NotificationType.PAYMENT_SUBMITTED, "B", "B");

        assertThat(notificationService.unreadCount(userId)).isEqualTo(before + 2);

        notificationService.markRead(n1.getId(), userId);

        assertThat(notificationService.unreadCount(userId)).isEqualTo(before + 1);
    }

    /**
     * Un second appel ne doit pas ecraser {@code readAt}. La comparaison
     * se fait contre la valeur relue en base (et non contre la reponse
     * du premier appel) : {@code Instant.now()} porte une precision
     * nanoseconde en memoire, tronquee a la microseconde une fois
     * persistee dans une colonne {@code TIMESTAMPTZ} -- comparer les
     * deux representations directement produirait un faux echec sans
     * rapport avec l'idempotence elle-meme.
     */
    @Test
    void markRead_isIdempotent() {
        UUID userId = createUser(RoleCode.USER).getId();
        Notification notification = notificationService.create(userId, NotificationType.QUOTE_CREATED, "A", "A");

        NotificationResponse first = notificationService.markRead(notification.getId(), userId);
        assertThat(first.readAt()).isNotNull();
        Instant persistedReadAt = notificationRepository.findById(notification.getId()).orElseThrow().getReadAt();

        NotificationResponse second = notificationService.markRead(notification.getId(), userId);

        assertThat(second.readAt()).isEqualTo(persistedReadAt);
    }

    @Test
    void markRead_onAnotherUsersNotification_returns404StyleError() {
        UUID owner = createUser(RoleCode.USER).getId();
        UUID intruder = createUser(RoleCode.USER).getId();
        Notification notification = notificationService.create(owner, NotificationType.QUOTE_CREATED, "A", "A");

        assertThatThrownBy(() -> notificationService.markRead(notification.getId(), intruder))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void listMine_returnsNewestFirst() {
        UUID userId = createUser(RoleCode.USER).getId();
        notificationService.create(userId, NotificationType.QUOTE_CREATED, "Premiere", "Premiere");
        notificationService.create(userId, NotificationType.PAYMENT_SUBMITTED, "Seconde", "Seconde");

        var page = notificationService.listMine(userId, PageRequest.of(0, 10));

        assertThat(page.content().get(0).title()).isEqualTo("Seconde");
        assertThat(page.content().get(1).title()).isEqualTo("Premiere");
    }
}
