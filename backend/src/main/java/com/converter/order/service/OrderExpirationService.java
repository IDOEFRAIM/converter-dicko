package com.converter.order.service;

import com.converter.notification.domain.NotificationType;
import com.converter.notification.service.NotificationService;
import com.converter.settings.domain.SettingKey;
import com.converter.settings.service.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Balayage des ordres en attente de paiement dont l'echeance est depassee : chacun est expire
 * ({@code AWAITING_PAYMENT -> EXPIRED}) et sa reservation de tresorerie CNY liberee, dans sa
 * propre transaction et sous verrou pessimiste (voir {@link OrderService#expireIfOverdue}).
 *
 * <p>Repond au probleme concret « une reservation de liquidite reste bloquee indefiniment parce
 * que le client ne paie ni n'annule » (docs/AUDIT_BUSINESS_LOGIC_PASS2.md, P2-1). Ce n'est pas un
 * scheduler « pour avoir un scheduler » : sans lui, la tresorerie CNY fuit.
 *
 * <p><b>Idempotent et rejouable</b> : {@code expireIfOverdue} re-verifie le statut ET l'echeance
 * apres avoir pris le verrou ; deux passages concurrents (ou un passage concurrent a un paiement
 * ou une annulation) se serialisent proprement sur la ligne, le perdant constate que l'ordre
 * n'est plus {@code AWAITING_PAYMENT} et ne fait rien.
 *
 * <p>{@code expireOverdue} prend l'instant de reference en parametre : le scheduler passe
 * {@code clock.instant()}, un test peut passer un instant arbitrairement futur pour verifier le
 * comportement sans attendre la fenetre reelle.
 */
@Service
public class OrderExpirationService {

    private static final Logger log = LoggerFactory.getLogger(OrderExpirationService.class);

    private final OrderService orderService;
    private final SettingsService settingsService;
    private final NotificationService notificationService;

    public OrderExpirationService(OrderService orderService,
                                  SettingsService settingsService,
                                  NotificationService notificationService) {
        this.orderService = orderService;
        this.settingsService = settingsService;
        this.notificationService = notificationService;
    }

    /**
     * Expire tous les ordres echus a {@code asOf}. Pilote par le parametre
     * {@code ORDER_AUTO_EXPIRE_ENABLED} : si desactive, ne fait rien (les ordres restent
     * {@code AWAITING_PAYMENT} et leur reservation active — decision administrative explicite).
     *
     * @return le nombre d'ordres effectivement expires par cet appel
     */
    public int expireOverdue(Instant asOf) {
        if (!settingsService.getBoolean(SettingKey.ORDER_AUTO_EXPIRE_ENABLED)) {
            return 0;
        }
        List<UUID> candidates = orderService.findOverdueOrderIds(asOf);
        int expired = 0;
        for (UUID orderId : candidates) {
            try {
                if (orderService.expireIfOverdue(orderId, asOf)) {
                    expired++;
                    UUID owner = orderService.ownerOf(orderId);
                    if (owner != null) {
                        notificationService.create(owner, NotificationType.ORDER_EXPIRED, "Ordre expire",
                                "Votre ordre a expire faute de paiement dans le delai imparti. "
                                        + "La reservation associee a ete liberee. Vous pouvez repartir d'un nouveau devis.");
                    }
                }
            } catch (RuntimeException ex) {
                // L'echec ou le retard d'un ordre n'affecte pas les autres.
                log.error("Echec de l'expiration de l'ordre {}", orderId, ex);
            }
        }
        if (expired > 0) {
            log.info("Expiration d'ordres : {} ordre(s) expire(s) sur {} candidat(s)", expired, candidates.size());
        }
        return expired;
    }
}
