package com.converter.order.scheduler;

import com.converter.order.service.OrderExpirationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Declenche periodiquement l'expiration des ordres en attente de paiement dont l'echeance est
 * depassee (voir {@link OrderExpirationService}). Meme patron que
 * {@code PreferredRateScheduler} : {@code fixedDelay} (jamais {@code fixedRate}) — le passage
 * suivant ne demarre qu'apres la fin du precedent, aucun chevauchement intra-instance ; la
 * garantie de fond en multi-instance/action concurrente reste le verrou pessimiste ligne par
 * ligne pris dans {@code OrderService.expireIfOverdue}.
 *
 * <p>Aucune logique metier ici : uniquement la cadence. L'activation reelle est pilotee par le
 * parametre {@code ORDER_AUTO_EXPIRE_ENABLED} (verifie dans le service).
 */
@Component
public class OrderExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderExpirationScheduler.class);

    private final OrderExpirationService orderExpirationService;
    private final Clock clock;

    public OrderExpirationScheduler(OrderExpirationService orderExpirationService, Clock clock) {
        this.orderExpirationService = orderExpirationService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${order.expiration.scheduler.fixed-delay-ms:60000}",
            initialDelayString = "${order.expiration.scheduler.initial-delay-ms:60000}")
    public void expireOverdueOrders() {
        try {
            orderExpirationService.expireOverdue(clock.instant());
        } catch (RuntimeException ex) {
            log.error("Echec du balayage d'expiration des ordres", ex);
        }
    }
}
