package com.converter.push.service;

import com.converter.common.util.JsonUtil;
import com.converter.config.props.PushProperties;
import com.converter.push.domain.PushSubscription;
import com.converter.push.repository.PushSubscriptionRepository;
import jakarta.annotation.PostConstruct;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.List;
import java.util.UUID;

/**
 * Envoi effectif des notifications Web Push (PWA, mission "blocages Apple/Meta" oct. 2026) --
 * appele en best-effort depuis {@code NotificationService.create}, jamais un pilier de la
 * notification interne elle-meme (deja persistee en base a ce stade). Fonctionnalite optionnelle
 * (voir {@link PushProperties}) : sans cle VAPID configuree, {@link #sendToUser} ne fait rien.
 *
 * <p>Chaque envoi est independant des autres (un abonnement expire/revoque -- HTTP 404/410 du
 * fournisseur de push -- ne doit jamais empecher l'envoi aux autres appareils du meme
 * utilisateur) ; l'abonnement fautif est alors supprime, jamais reessaye.
 */
@Service
public class PushNotificationSender {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationSender.class);

    private final PushProperties properties;
    private final PushSubscriptionRepository subscriptionRepository;
    private PushService pushService;

    public PushNotificationSender(PushProperties properties, PushSubscriptionRepository subscriptionRepository) {
        this.properties = properties;
        this.subscriptionRepository = subscriptionRepository;
    }

    @PostConstruct
    void init() {
        if (!properties.configured()) {
            log.info("Notifications push non configurees (VAPID_PUBLIC_KEY/VAPID_PRIVATE_KEY absents) -- envoi desactive.");
            return;
        }
        Security.addProvider(new BouncyCastleProvider());
        try {
            PushService service = new PushService(properties.publicKey(), properties.privateKey());
            if (properties.subject() != null && !properties.subject().isBlank()) {
                service.setSubject(properties.subject());
            }
            this.pushService = service;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Impossible d'initialiser le service Web Push (cles VAPID invalides).", e);
        }
    }

    /**
     * Envoie a TOUS les abonnements actifs de {@code userId} -- jamais lance dans la transaction
     * appelante (voir {@code NotificationService.create}, deja {@code REQUIRES_NEW} et propre
     * avant cet appel) : un echec d'envoi push ne doit jamais remonter comme un echec de la
     * notification interne, deja persistee avec succes.
     */
    public void sendToUser(UUID userId, String title, String message) {
        if (pushService == null) {
            return;
        }
        List<PushSubscription> subscriptions = subscriptionRepository.findByUserId(userId);
        for (PushSubscription subscription : subscriptions) {
            send(subscription, title, message);
        }
    }

    private void send(PushSubscription subscription, String title, String message) {
        String payload = "{\"title\":" + JsonUtil.jsonString(title) + ",\"body\":" + JsonUtil.jsonString(message) + "}";
        try {
            Notification notification = new Notification(
                    subscription.getEndpoint(), subscription.getP256dhKey(), subscription.getAuthKey(), payload);
            HttpResponse response = pushService.send(notification);
            int status = response.getStatusLine().getStatusCode();
            if (status == 404 || status == 410) {
                // Abonnement revoque/expire cote fournisseur de push (utilisateur a desinstalle,
                // vide le stockage du navigateur...) -- jamais reessaye, simplement oublie.
                subscriptionRepository.deleteByEndpoint(subscription.getEndpoint());
            } else if (status >= 300) {
                log.warn("Envoi push refuse (statut {}) pour l'abonnement {}", status, subscription.getId());
            }
        } catch (Exception e) {
            // Chiffrement, reseau, fournisseur de push indisponible... jamais propage : voir la
            // Javadoc de classe, un incident d'envoi push ne doit jamais affecter autre chose.
            log.warn("Echec d'envoi push pour l'abonnement {}", subscription.getId(), e);
        }
    }
}
