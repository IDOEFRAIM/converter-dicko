package com.converter.push.service;

import com.converter.push.domain.PushSubscription;
import com.converter.push.dto.SubscribePushRequest;
import com.converter.push.repository.PushSubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Gestion des abonnements Web Push (PWA, mission "blocages Apple/Meta" oct. 2026) : un simple
 * carnet endpoint -> utilisateur, jamais de logique metier. L'envoi effectif est {@link
 * PushNotificationSender}, invoque depuis {@code NotificationService.create} -- ce service-ci ne
 * connait que le stockage des abonnements.
 */
@Service
public class PushSubscriptionService {

    private final PushSubscriptionRepository repository;
    private final Clock clock;

    public PushSubscriptionService(PushSubscriptionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Idempotent : un meme {@code endpoint} resouscrit (ex. apres une purge locale du navigateur)
     * remplace simplement l'abonnement existant plutot que de heurter la contrainte unique.
     */
    @Transactional
    public void subscribe(UUID userId, SubscribePushRequest request) {
        repository.findByEndpoint(request.endpoint()).ifPresent(repository::delete);
        repository.flush();
        PushSubscription subscription = new PushSubscription(
                userId, request.endpoint(), request.keys().p256dh(), request.keys().auth(), clock.instant());
        repository.save(subscription);
    }

    @Transactional
    public void unsubscribe(String endpoint) {
        repository.deleteByEndpoint(endpoint);
    }
}
