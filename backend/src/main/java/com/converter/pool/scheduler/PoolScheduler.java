package com.converter.pool.scheduler;

import com.converter.pool.service.PoolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Expire les Ruees ACTIVE dont l'echeance est depassee. Cadence courte (30s par defaut) --
 * contrairement a {@code RateAlertScheduler}/{@code PreferredRateScheduler} (5 min), une Ruee dure
 * typiquement 5 a 180 minutes et son urgence collective (mission "differenciation marketing")
 * exige une expiration ressentie comme quasi immediate, pas a 5 minutes pres.
 *
 * <p>Meme patron que les autres schedulers du projet : {@code fixedDelay} (jamais {@code
 * fixedRate}), chaque pool traite dans sa propre transaction sous verrou pessimiste, un {@code
 * catch RuntimeException} par element pour qu'un echec ponctuel n'affecte jamais les autres
 * candidats ni ne tue le scheduler.
 */
@Component
public class PoolScheduler {

    private static final Logger log = LoggerFactory.getLogger(PoolScheduler.class);

    private final PoolService poolService;

    public PoolScheduler(PoolService poolService) {
        this.poolService = poolService;
    }

    @Scheduled(fixedDelayString = "${pool.scheduler.fixed-delay-ms:30000}",
            initialDelayString = "${pool.scheduler.initial-delay-ms:30000}")
    public void expireOverduePools() {
        List<UUID> candidates = poolService.activePoolIds();
        for (UUID id : candidates) {
            try {
                poolService.processExpiration(id);
            } catch (RuntimeException ex) {
                log.error("Echec de l'evaluation de la Ruee {}", id, ex);
            }
        }
    }
}
