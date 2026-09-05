package com.converter.common.idempotency.scheduler;

import com.converter.common.idempotency.IdempotencyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Recupere periodiquement les cles d'idempotence restees "pending" apres un crash serveur (voir
 * {@link IdempotencyService#reclaimStalePending} pour la preuve de securite transactionnelle).
 *
 * <p>Meme patron que {@code OrderExpirationScheduler}/{@code PreferredRateScheduler} :
 * {@code fixedDelay} (jamais {@code fixedRate}, aucun chevauchement intra-instance),
 * {@code catch RuntimeException} pour qu'un echec ponctuel du balayage ne tue jamais le
 * scheduler. La suppression elle-meme est une simple requete {@code DELETE ... WHERE
 * response_status IS NULL AND created_at < seuil}, sure en multi-instance sans verrou
 * supplementaire : PostgreSQL serialise naturellement toute suppression contre l'{@code UPDATE}
 * pris par {@code complete()} sur la meme ligne (voir la Javadoc de {@code reclaimStalePending}).
 *
 * <p>{@code idempotency.pending-timeout-ms} par defaut a 15 minutes : tres largement superieur a
 * la duree reelle de n'importe laquelle des operations gardees (creation d'ordre, soumission de
 * paiement, execution d'un reglement, remboursement, mouvement de tresorerie manuel — aucune
 * n'effectue d'I/O externe ni de traitement de fichier, toutes commitent en quelques millisecondes
 * a quelques secondes meme sous forte contention).
 */
@Component
public class IdempotencyPendingCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyPendingCleanupScheduler.class);

    private final IdempotencyService idempotencyService;
    private final Duration pendingTimeout;

    public IdempotencyPendingCleanupScheduler(
            IdempotencyService idempotencyService,
            @Value("${idempotency.pending-timeout-ms:900000}") long pendingTimeoutMs) {
        this.idempotencyService = idempotencyService;
        this.pendingTimeout = Duration.ofMillis(pendingTimeoutMs);
    }

    @Scheduled(fixedDelayString = "${idempotency.cleanup-scheduler.fixed-delay-ms:300000}",
            initialDelayString = "${idempotency.cleanup-scheduler.initial-delay-ms:300000}")
    public void reclaimStalePendingKeys() {
        try {
            idempotencyService.reclaimStalePending(pendingTimeout);
        } catch (RuntimeException ex) {
            log.error("Echec du balayage de recuperation des cles d'idempotence 'pending'", ex);
        }
    }
}
