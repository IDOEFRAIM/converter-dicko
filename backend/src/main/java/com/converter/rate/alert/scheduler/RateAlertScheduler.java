package com.converter.rate.alert.scheduler;

import com.converter.rate.alert.service.RateAlertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Evalue periodiquement les alertes de taux actives. Meme patron que
 * {@code PreferredRateScheduler}/{@code OrderExpirationScheduler} : {@code fixedDelay} (jamais
 * {@code fixedRate} — le passage suivant ne demarre qu'apres la fin complet du precedent, aucun
 * chevauchement intra-instance), chaque alerte traitee dans sa propre transaction
 * ({@code RateAlertService#processOne}) sous verrou pessimiste (garantie de fond en
 * multi-instance ou action concurrente), et un {@code catch RuntimeException} par element pour
 * qu'un echec ponctuel n'affecte jamais les autres candidats ni ne tue le scheduler.
 *
 * <p>Frequence par defaut 5 minutes — cadence raisonnable pour un pilote &lt;50 utilisateurs,
 * configurable via {@code rate-alert.scheduler.fixed-delay-ms} comme les autres schedulers du
 * projet (jamais via {@code SettingsService} : la cadence d'un job planifie est une
 * configuration d'infrastructure, pas un parametre metier modifiable a chaud).
 */
@Component
public class RateAlertScheduler {

    private static final Logger log = LoggerFactory.getLogger(RateAlertScheduler.class);

    private final RateAlertService rateAlertService;

    public RateAlertScheduler(RateAlertService rateAlertService) {
        this.rateAlertService = rateAlertService;
    }

    @Scheduled(fixedDelayString = "${rate-alert.scheduler.fixed-delay-ms:300000}",
            initialDelayString = "${rate-alert.scheduler.initial-delay-ms:300000}")
    public void evaluateActiveAlerts() {
        List<UUID> candidates = rateAlertService.activeAlertIds();
        for (UUID id : candidates) {
            try {
                rateAlertService.processOne(id);
            } catch (RuntimeException ex) {
                log.error("Echec de l'evaluation de l'alerte de taux {}", id, ex);
            }
        }
    }
}
