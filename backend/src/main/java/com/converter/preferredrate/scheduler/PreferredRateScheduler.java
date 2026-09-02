package com.converter.preferredrate.scheduler;

import com.converter.preferredrate.service.PreferredRateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Evalue periodiquement les demandes de taux preferentiel actives et
 * les echanges en cours. Aucune source de taux externe : la cotation
 * utilisee reste exclusivement celle deja fournie par
 * {@code ManualRateProvider} (voir {@code PreferredRateService}).
 *
 * <p>Chaque demande/echange est traite dans <b>sa propre transaction</b>
 * ({@code PreferredRateService#processOne}/{@code #progressOne}), sous
 * verrou pessimiste : l'echec ou le retard d'un element n'affecte pas
 * les autres, et deux passages qui se chevaucheraient (ou un passage
 * concurrent a une action utilisateur) se serialisent proprement sur
 * chaque ligne plutot que d'entrer en collision.
 *
 * <p>{@code fixedDelay} (et non {@code fixedRate}) : le prochain passage
 * ne demarre qu'apres la fin complete du precedent -- impossible que
 * deux executions du scheduler se chevauchent au sein d'une meme
 * instance, ce qui simplifie deja une bonne part du raisonnement sur la
 * concurrence, le verrou pessimiste restant la garantie de fond
 * (plusieurs instances, ou action utilisateur simultanee).
 */
@Component
public class PreferredRateScheduler {

    private static final Logger log = LoggerFactory.getLogger(PreferredRateScheduler.class);

    private final PreferredRateService preferredRateService;

    public PreferredRateScheduler(PreferredRateService preferredRateService) {
        this.preferredRateService = preferredRateService;
    }

    @Scheduled(fixedDelayString = "${preferred-rate.scheduler.fixed-delay-ms:30000}",
            initialDelayString = "${preferred-rate.scheduler.initial-delay-ms:30000}")
    public void evaluateActiveRequests() {
        List<UUID> candidates = preferredRateService.activeRequestIds();
        for (UUID id : candidates) {
            try {
                preferredRateService.processOne(id);
            } catch (RuntimeException ex) {
                log.error("Echec de l'evaluation de la demande de taux preferentiel {}", id, ex);
            }
        }
    }

    @Scheduled(fixedDelayString = "${preferred-rate.scheduler.fixed-delay-ms:30000}",
            initialDelayString = "${preferred-rate.scheduler.initial-delay-ms:30000}")
    public void progressActiveExchanges() {
        List<UUID> candidates = preferredRateService.startedExchangeIds();
        for (UUID id : candidates) {
            try {
                preferredRateService.progressOne(id);
            } catch (RuntimeException ex) {
                log.error("Echec de la progression de l'echange {}", id, ex);
            }
        }
    }
}
