package com.converter.rate.alert.service;

import com.converter.audit.domain.AuditAction;
import com.converter.audit.service.AuditService;
import com.converter.common.api.PageResponse;
import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.notification.domain.NotificationType;
import com.converter.notification.service.NotificationService;
import com.converter.preferredrate.domain.PreferredRateDirection;
import com.converter.rate.alert.domain.RateAlert;
import com.converter.rate.alert.domain.RateAlertStatus;
import com.converter.rate.alert.domain.RateComparison;
import com.converter.rate.alert.dto.CreateRateAlertRequest;
import com.converter.rate.alert.dto.RateAlertResponse;
import com.converter.rate.alert.repository.RateAlertRepository;
import com.converter.rate.provider.RateProvider;
import com.converter.rate.publicrate.service.PublicRateSnapshotService;
import com.converter.security.OwnershipService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestration des alertes de taux : creation, consultation, annulation client, et les deux
 * transitions pilotees par {@code RateAlertScheduler} — expiration et declenchement.
 *
 * <p><b>Regle absolue</b> : le taux compare a l'objectif utilisateur est toujours le dernier
 * {@code customerRate} public ({@link PublicRateSnapshotService#latestCustomerRate}), jamais un
 * {@code breakEvenRate}, une marge interne, ou toute donnee de
 * {@code daily_cost_rate_configurations}/{@code rate_sources}. Ce service ne depend d'aucun de
 * ces types — garantie structurelle, comme pour {@code PublicRateSnapshotService} lui-meme.
 *
 * <p><b>Aucune transaction financiere</b> : contrairement a {@code PreferredRateService}, ce
 * service ne cree ni ne modifie jamais de {@code Quote}/{@code Order}/{@code Payment}/
 * {@code Settlement}/{@code Refund}, et ne touche jamais au {@code Wallet} ni a la
 * {@code Treasury} — une {@code RateAlert} est une notification, jamais un ordre d'execution.
 */
@Service
public class RateAlertService {

    private static final Logger log = LoggerFactory.getLogger(RateAlertService.class);

    private final RateAlertRepository repository;
    private final PublicRateSnapshotService publicRateSnapshotService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final OwnershipService ownershipService;
    private final Clock clock;

    public RateAlertService(RateAlertRepository repository,
                            PublicRateSnapshotService publicRateSnapshotService,
                            NotificationService notificationService,
                            AuditService auditService,
                            OwnershipService ownershipService,
                            Clock clock) {
        this.repository = repository;
        this.publicRateSnapshotService = publicRateSnapshotService;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.ownershipService = ownershipService;
        this.clock = clock;
    }

    @Transactional
    public RateAlertResponse create(CreateRateAlertRequest request, UUID userId) {
        String currencyPair = request.currencyPair() == null ? RateProvider.DEFAULT_CURRENCY_PAIR : request.currencyPair();
        if (!RateProvider.DEFAULT_CURRENCY_PAIR.equals(currencyPair)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Paire de devises non supportee : " + currencyPair + ".");
        }
        PreferredRateDirection direction = request.direction() == null ? PreferredRateDirection.XOF_TO_CNY : request.direction();
        RateComparison comparison = request.comparison() == null ? RateComparison.LESS_THAN_OR_EQUAL : request.comparison();

        Instant now = clock.instant();
        if (request.expiresAt() != null && !request.expiresAt().isAfter(now)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "La date d'expiration doit etre future.");
        }

        RateAlert saved = repository.save(new RateAlert(userId, currencyPair, direction, request.targetRate(),
                comparison, now, request.expiresAt()));

        auditService.record(userId, null, AuditAction.RATE_ALERT_CREATED, "RateAlert", saved.getId().toString(), null);
        log.info("Alerte de taux {} creee pour {} ({} {} {})", saved.getId(), userId, currencyPair, comparison,
                request.targetRate());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public RateAlertResponse get(UUID id, UUID userId) {
        RateAlert alert = repository.findById(id).orElseThrow(() -> notFound(id));
        ownershipService.assertOwnedBy(alert.getUserId(), userId, ErrorCode.RATE_ALERT_NOT_FOUND,
                "Alerte de taux introuvable : " + id);
        return toResponse(alert);
    }

    @Transactional(readOnly = true)
    public PageResponse<RateAlertResponse> listMine(UUID userId, RateAlertStatus status, Pageable pageable) {
        Page<RateAlert> page = status == null
                ? repository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                : repository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status, pageable);
        return PageResponse.from(page, RateAlertService::toResponse);
    }

    @Transactional
    public RateAlertResponse cancel(UUID id, UUID userId) {
        RateAlert alert = repository.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
        ownershipService.assertOwnedBy(alert.getUserId(), userId, ErrorCode.RATE_ALERT_NOT_FOUND,
                "Alerte de taux introuvable : " + id);

        alert.cancel(clock.instant());
        auditService.record(userId, null, AuditAction.RATE_ALERT_CANCELLED, "RateAlert", alert.getId().toString(), null);
        log.info("Alerte de taux {} annulee par {}", id, userId);
        return toResponse(alert);
    }

    // -----------------------------------------------------------------
    // Pilote par RateAlertScheduler -- une transaction par alerte, verrou pessimiste pris a
    // l'interieur (voir findByIdForUpdate).
    // -----------------------------------------------------------------

    /** Identifiants candidats a evaluer : toute alerte encore ACTIVE. */
    @Transactional(readOnly = true)
    public List<UUID> activeAlertIds() {
        return repository.findIdsByStatus(RateAlertStatus.ACTIVE);
    }

    /**
     * Traite une seule alerte : expire si l'echeance est depassee (l'expiration l'emporte
     * toujours sur l'evaluation du taux, meme si la condition est par ailleurs satisfaite),
     * sinon declenche si le dernier taux client public satisfait la regle de comparaison.
     * No-op silencieux si l'alerte n'est plus {@code ACTIVE} (deja traitee par un autre appel
     * concurrent — scheduler ou annulation utilisateur) : c'est le double-check, sous verrou
     * pessimiste, qui garantit qu'une alerte n'est jamais declenchee — ni notifiee — deux fois.
     */
    @Transactional
    public void processOne(UUID alertId) {
        RateAlert alert = repository.findByIdForUpdate(alertId).orElse(null);
        if (alert == null || alert.getStatus() != RateAlertStatus.ACTIVE) {
            return;
        }
        Instant now = clock.instant();

        if (alert.isPastDeadline(now)) {
            alert.expire(now);
            log.info("Alerte de taux {} expiree", alertId);
            return;
        }

        Optional<BigDecimal> currentRate = publicRateSnapshotService.latestCustomerRate(alert.getCurrencyPair());
        if (currentRate.isEmpty()) {
            // Aucun taux client public encore publie pour cette paire : reevaluee au prochain
            // passage du scheduler, jusqu'a expiration eventuelle. Ni declenchee, ni marquee en echec.
            return;
        }

        if (alert.isSatisfiedBy(currentRate.get())) {
            // Ordre volontaire : la transition metier (mutation en memoire, flush a la fin de
            // cette meme transaction) precede tout effet de bord isole (audit puis notification,
            // chacun REQUIRES_NEW). Faire l'inverse risquerait qu'une exception entre l'envoi et
            // la transition laisse l'alerte ACTIVE apres notification -- exposee a une seconde
            // notification au prochain passage. Meme principe que PreferredRateService#trigger.
            alert.trigger(now);
            auditService.recordSystem(AuditAction.RATE_ALERT_TRIGGERED, "RateAlert", alert.getId().toString(), null);
            notificationService.create(alert.getUserId(), NotificationType.RATE_ALERT_TRIGGERED,
                    "Objectif de taux atteint",
                    "Le taux client " + alert.getCurrencyPair() + " a atteint votre objectif de "
                            + alert.getTargetRate() + " (taux actuel : " + currentRate.get() + ").");
            log.info("Alerte de taux {} declenchee (cible {}, taux actuel {})", alertId, alert.getTargetRate(),
                    currentRate.get());
        }
    }

    // -----------------------------------------------------------------

    private BusinessException notFound(UUID id) {
        return new BusinessException(ErrorCode.RATE_ALERT_NOT_FOUND, "Alerte de taux introuvable : " + id);
    }

    private static RateAlertResponse toResponse(RateAlert alert) {
        return new RateAlertResponse(alert.getId(), alert.getCurrencyPair(), alert.getDirection(),
                alert.getTargetRate(), alert.getComparison(), alert.getStatus(), alert.getCreatedAt(),
                alert.getExpiresAt(), alert.getTriggeredAt(), alert.getCancelledAt(), alert.getExpiredAt());
    }
}
