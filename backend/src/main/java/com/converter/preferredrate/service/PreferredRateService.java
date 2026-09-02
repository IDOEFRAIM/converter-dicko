package com.converter.preferredrate.service;

import com.converter.common.api.PageResponse;
import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.notification.domain.NotificationType;
import com.converter.notification.service.NotificationService;
import com.converter.preferredrate.domain.Exchange;
import com.converter.preferredrate.domain.ExchangeStatus;
import com.converter.preferredrate.domain.PreferredRateDirection;
import com.converter.preferredrate.domain.PreferredRateRequest;
import com.converter.preferredrate.domain.PreferredRateStatus;
import com.converter.preferredrate.dto.CreatePreferredRateRequest;
import com.converter.preferredrate.dto.ExchangeSummaryResponse;
import com.converter.preferredrate.dto.PreferredRatePhase;
import com.converter.preferredrate.dto.PreferredRateRequestResponse;
import com.converter.preferredrate.repository.ExchangeRepository;
import com.converter.preferredrate.repository.PreferredRateRequestRepository;
import com.converter.rate.domain.MarketRate;
import com.converter.rate.engine.AmountBasis;
import com.converter.rate.engine.PricingResult;
import com.converter.rate.engine.RateEngine;
import com.converter.rate.provider.RateProvider;
import com.converter.security.OwnershipService;
import com.converter.settings.domain.SettingKey;
import com.converter.settings.service.SettingsService;
import com.converter.wallet.service.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Orchestration des demandes de taux preferentiel : creation (reserve
 * le Wallet), consultation (taux courant recalcule a chaque lecture),
 * annulation client, et les deux transitions pilotees par
 * {@code PreferredRateScheduler} : expiration et declenchement.
 *
 * <p>Le montant est <b>immobilise sur le Wallet des la creation</b>
 * (RESERVE) -- pas seulement au moment ou le taux est atteint : c'est
 * la seule maniere de garantir qu'un meme solde ne puisse jamais servir
 * deux demandes a la fois (section "Immutabilite et concurrence"). Le
 * verrou pessimiste pris par {@code WalletRepository#findByUserIdForUpdate}
 * lors de la reservation serialise deja les creations concurrentes sur
 * le meme Wallet.
 *
 * <p><b>Condition de declenchement (SEND_XOF)</b> : le taux cible est
 * atteint quand le taux client COURANT devient superieur ou egal au
 * taux cible ({@code currentRate >= targetRate}), jamais avant --
 * {@code currentRate < targetRate} reste {@code WAITING}. Une demande
 * "cible 90" avec un taux courant de 86.275 n'est donc pas declenchee ;
 * elle l'est des que le taux courant atteint 90.
 */
@Service
public class PreferredRateService {

    private static final Logger log = LoggerFactory.getLogger(PreferredRateService.class);

    /** Duree exacte de validite d'une demande, imposee par la specification. */
    public static final Duration VALIDITY = Duration.ofDays(3);

    private final PreferredRateRequestRepository requestRepository;
    private final ExchangeRepository exchangeRepository;
    private final WalletService walletService;
    private final NotificationService notificationService;
    private final RateProvider rateProvider;
    private final RateEngine rateEngine;
    private final SettingsService settingsService;
    private final OwnershipService ownershipService;
    private final Clock clock;

    public PreferredRateService(PreferredRateRequestRepository requestRepository,
                                ExchangeRepository exchangeRepository,
                                WalletService walletService,
                                NotificationService notificationService,
                                RateProvider rateProvider,
                                RateEngine rateEngine,
                                SettingsService settingsService,
                                OwnershipService ownershipService,
                                Clock clock) {
        this.requestRepository = requestRepository;
        this.exchangeRepository = exchangeRepository;
        this.walletService = walletService;
        this.notificationService = notificationService;
        this.rateProvider = rateProvider;
        this.rateEngine = rateEngine;
        this.settingsService = settingsService;
        this.ownershipService = ownershipService;
        this.clock = clock;
    }

    @Transactional
    public PreferredRateRequestResponse create(CreatePreferredRateRequest request, UUID userId) {
        if (request.direction() != PreferredRateDirection.XOF_TO_CNY) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Seule la direction XOF_TO_CNY est supportee.");
        }
        Instant now = clock.instant();
        PreferredRateRequest saved = requestRepository.save(new PreferredRateRequest(
                userId, request.direction(), request.amountXof(), request.targetRate(), now, now.plus(VALIDITY)));

        // Immobilise le montant immediatement : voir la Javadoc de classe.
        walletService.reserve(userId, request.amountXof(), saved.getId(), "Demande de taux preferentiel " + saved.getId());

        log.info("Demande de taux preferentiel {} creee pour {} ({} XOF a {} XOF/CNY)",
                saved.getId(), userId, request.amountXof(), request.targetRate());
        return toResponse(saved, null);
    }

    @Transactional
    public PreferredRateRequestResponse get(UUID id, UUID userId) {
        PreferredRateRequest preferredRate = requestRepository.findById(id).orElseThrow(() -> notFound(id));
        ownershipService.assertOwnedBy(preferredRate.getUserId(), userId, ErrorCode.PREFERRED_RATE_NOT_FOUND,
                "Demande de taux preferentiel introuvable : " + id);
        return toResponse(preferredRate, findExchange(preferredRate));
    }

    /**
     * PAS {@code readOnly = true} : {@code toResponse} peut declencher
     * {@link #currentPricing} pour chaque demande encore {@code ACTIVE},
     * qui verrouille la cotation courante ({@code SELECT ... FOR SHARE},
     * voir {@code RateSourceRepository#findCurrentForPricing}) -- Postgres
     * refuse ce verrou dans une transaction strictement en lecture seule.
     */
    @Transactional
    public PageResponse<PreferredRateRequestResponse> listMine(UUID userId, Pageable pageable) {
        return PageResponse.from(requestRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable),
                p -> toResponse(p, findExchange(p)));
    }

    @Transactional
    public PreferredRateRequestResponse cancel(UUID id, UUID userId) {
        PreferredRateRequest preferredRate = requestRepository.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
        ownershipService.assertOwnedBy(preferredRate.getUserId(), userId, ErrorCode.PREFERRED_RATE_NOT_FOUND,
                "Demande de taux preferentiel introuvable : " + id);

        preferredRate.cancel(clock.instant());
        walletService.release(userId, preferredRate.getAmountXof(), preferredRate.getId(), "Annulation par le client");

        log.info("Demande de taux preferentiel {} annulee par {}", id, userId);
        return toResponse(preferredRate, null);
    }

    // -----------------------------------------------------------------
    // Pilote par PreferredRateScheduler -- une transaction par demande,
    // verrou pessimiste pris a l'interieur (voir findByIdForUpdate).
    // -----------------------------------------------------------------

    /** Identifiants candidats a evaluer : toute demande encore ACTIVE. */
    @Transactional(readOnly = true)
    public List<UUID> activeRequestIds() {
        return requestRepository.findIdsByStatus(PreferredRateStatus.ACTIVE);
    }

    /**
     * Traite une seule demande : expire si J+3 est depasse, sinon
     * declenche l'echange si le taux courant a atteint ou depasse le
     * taux cible. No-op silencieux si la demande n'est plus ACTIVE
     * (deja traitee par un autre appel concurrent -- scheduler ou
     * annulation utilisateur) : c'est le double-check qui garantit
     * qu'une demande n'est executee qu'une seule fois.
     */
    @Transactional
    public void processOne(UUID requestId) {
        PreferredRateRequest preferredRate = requestRepository.findByIdForUpdate(requestId).orElse(null);
        if (preferredRate == null || preferredRate.getStatus() != PreferredRateStatus.ACTIVE) {
            return;
        }
        Instant now = clock.instant();

        if (preferredRate.isPastDeadline(now)) {
            expire(preferredRate, now);
            return;
        }

        PricingResult pricing;
        try {
            pricing = currentPricing(preferredRate.getAmountXof());
        } catch (BusinessException ex) {
            // Aucune cotation manuelle publiee pour l'instant : cette demande sera reevaluee au
            // prochain passage du scheduler, jusqu'a expiration eventuelle a J+3.
            //
            // NB : on ne sonde PAS d'abord via rateProvider.isAvailable(...) ici. RateSource est
            // une entite @Immutable ; une lecture non verrouillee (isAvailable) suivie, dans la
            // MEME transaction/persistence context, de la lecture verrouillee PESSIMISTIC_READ de
            // currentPricing sur la meme ligne fait echouer Hibernate ("Lock mode not supported" :
            // impossible d'elever le lock mode d'une entite immuable deja gerее). Le try/catch
            // reste donc la forme correcte a cet endroit precis.
            return;
        }

        // Correction : pour SEND_XOF, le taux cible est atteint quand le
        // taux client COURANT est superieur ou egal au taux cible (jamais
        // avant) -- ex. cible 90, taux courant 86.275 => en attente ;
        // taux courant 90 ou plus => declenchement. "Le systeme choisit
        // le premier taux valide rencontre" : des que la condition est
        // vraie a un passage du scheduler, elle declenche immediatement.
        if (pricing.customerRate().compareTo(preferredRate.getTargetRate()) >= 0) {
            trigger(preferredRate, pricing, now);
        }
    }

    /** Fait progresser les echanges en cours (T+45min, T+90min, terminaison a 2h max). Voir Exchange. */
    @Transactional
    public void progressOne(UUID exchangeId) {
        Exchange exchange = exchangeRepository.findByIdForUpdate(exchangeId).orElse(null);
        if (exchange == null) {
            return;
        }
        Instant now = clock.instant();

        if (exchange.isCompletionDue(now)) {
            exchange.complete(now);
            notificationService.create(exchange.getUserId(), NotificationType.EXCHANGE_COMPLETED,
                    "Echange termine",
                    "Votre echange de " + exchange.getAmountXof() + " XOF vers " + exchange.getAmountCny()
                            + " CNY est termine.");
            log.info("Echange {} termine", exchange.getId());
            return;
        }
        if (exchange.isProgress90Due(now)) {
            exchange.markProgress90Sent(now);
            notificationService.create(exchange.getUserId(), NotificationType.EXCHANGE_PROGRESS,
                    "Echange en cours", "Votre echange progresse toujours (1h30 ecoulees).");
        } else if (exchange.isProgress45Due(now)) {
            exchange.markProgress45Sent(now);
            notificationService.create(exchange.getUserId(), NotificationType.EXCHANGE_PROGRESS,
                    "Echange en cours", "Votre echange progresse (45 minutes ecoulees).");
        }
    }

    @Transactional(readOnly = true)
    public List<UUID> startedExchangeIds() {
        return exchangeRepository.findIdsByStatus(ExchangeStatus.STARTED);
    }

    // -----------------------------------------------------------------

    private void expire(PreferredRateRequest preferredRate, Instant now) {
        preferredRate.expire(now);
        walletService.release(preferredRate.getUserId(), preferredRate.getAmountXof(), preferredRate.getId(),
                "Expiration a J+3 sans atteinte du taux cible");
        notificationService.create(preferredRate.getUserId(), NotificationType.PREFERRED_RATE_EXPIRED,
                "Taux preferentiel expire",
                "Votre demande de taux preferentiel (" + preferredRate.getAmountXof() + " XOF a "
                        + preferredRate.getTargetRate() + " XOF/CNY) a expire sans atteindre le taux cible. "
                        + "Le montant a ete restitue a votre solde disponible.");
        log.info("Demande de taux preferentiel {} expiree (J+3)", preferredRate.getId());
    }

    private void trigger(PreferredRateRequest preferredRate, PricingResult pricing, Instant now) {
        Exchange exchange = exchangeRepository.save(new Exchange(
                preferredRate.getUserId(), preferredRate.getId(), preferredRate.getAmountXof(),
                pricing.customerRate(), pricing.amountCny(), now));

        walletService.debit(preferredRate.getUserId(), preferredRate.getAmountXof(), preferredRate.getId(),
                "Echange declenche par taux preferentiel " + preferredRate.getId());

        preferredRate.execute(now, pricing.customerRate(), exchange.getId());

        notificationService.create(preferredRate.getUserId(), NotificationType.PREFERRED_RATE_REACHED,
                "Taux cible atteint",
                "Le taux cible de " + preferredRate.getTargetRate() + " XOF/CNY a ete atteint (taux reel : "
                        + pricing.customerRate() + "). L'echange demarre.");
        notificationService.create(preferredRate.getUserId(), NotificationType.EXCHANGE_STARTED,
                "Echange demarre",
                "Votre echange de " + preferredRate.getAmountXof() + " XOF vers " + pricing.amountCny()
                        + " CNY a demarre.");

        log.info("Demande de taux preferentiel {} declenchee : taux atteint {} (cible {}), echange {}",
                preferredRate.getId(), pricing.customerRate(), preferredRate.getTargetRate(), exchange.getId());
    }

    private PricingResult currentPricing(BigDecimal amountXof) {
        MarketRate marketRate = rateProvider.currentRate(RateProvider.DEFAULT_CURRENCY_PAIR);
        BigDecimal marginPercentage = settingsService.getDecimal(SettingKey.DEFAULT_MARGIN_PERCENTAGE);
        BigDecimal feePercentage = settingsService.getDecimal(SettingKey.DEFAULT_FEE_PERCENTAGE);
        BigDecimal fixedFeeXof = settingsService.getDecimal(SettingKey.DEFAULT_FIXED_FEE_XOF);
        return rateEngine.price(AmountBasis.XOF, amountXof, marketRate, marginPercentage, feePercentage, fixedFeeXof);
    }

    private Exchange findExchange(PreferredRateRequest preferredRate) {
        if (preferredRate.getExchangeId() == null) {
            return null;
        }
        return exchangeRepository.findByPreferredRateRequestId(preferredRate.getId()).orElse(null);
    }

    private BusinessException notFound(UUID id) {
        return new BusinessException(ErrorCode.PREFERRED_RATE_NOT_FOUND, "Demande de taux preferentiel introuvable : " + id);
    }

    private PreferredRateRequestResponse toResponse(PreferredRateRequest preferredRate, Exchange exchange) {
        BigDecimal currentRate = null;
        BigDecimal gap = null;
        if (preferredRate.getStatus() == PreferredRateStatus.ACTIVE) {
            try {
                currentRate = currentPricing(preferredRate.getAmountXof()).customerRate();
                gap = currentRate.subtract(preferredRate.getTargetRate());
            } catch (BusinessException ex) {
                // Aucune cotation courante : voir la note dans processOne (entite RateSource
                // @Immutable + PESSIMISTIC_READ ne tolerent pas une sonde non verrouillee prealable).
                currentRate = null;
            }
        }

        PreferredRatePhase phase = toPhase(preferredRate, exchange);
        ExchangeSummaryResponse exchangeResponse = exchange == null ? null : toExchangeSummary(exchange);

        return new PreferredRateRequestResponse(
                preferredRate.getId(), preferredRate.getDirection(), preferredRate.getAmountXof(),
                preferredRate.getTargetRate(), currentRate, gap, phase, preferredRate.getStatus(),
                preferredRate.getAchievedRate(), preferredRate.getCreatedAt(), preferredRate.getExpiresAt(),
                preferredRate.getExecutedAt(), preferredRate.getExpiredAt(), preferredRate.getCancelledAt(),
                exchangeResponse);
    }

    /** Une seule des deux vues frontend est pertinente a la fois -- jamais le compte a rebours J+3 pendant un echange en cours. */
    private PreferredRatePhase toPhase(PreferredRateRequest preferredRate, Exchange exchange) {
        return switch (preferredRate.getStatus()) {
            case ACTIVE -> PreferredRatePhase.WAITING;
            case EXPIRED -> PreferredRatePhase.EXPIRED;
            case CANCELLED -> PreferredRatePhase.CANCELLED;
            case EXECUTED -> (exchange != null && exchange.getStatus() == ExchangeStatus.COMPLETED)
                    ? PreferredRatePhase.EXCHANGE_COMPLETED
                    : PreferredRatePhase.EXCHANGE_IN_PROGRESS;
        };
    }

    private ExchangeSummaryResponse toExchangeSummary(Exchange exchange) {
        Instant deadlineAt = exchange.getStartedAt().plus(Exchange.MAX_DURATION);
        Instant nextUpdateAt = null;
        String stage;
        if (exchange.getStatus() == ExchangeStatus.COMPLETED) {
            stage = "COMPLETED";
        } else if (exchange.getProgress90SentAt() != null) {
            stage = "PROGRESS_90";
            nextUpdateAt = deadlineAt;
        } else if (exchange.getProgress45SentAt() != null) {
            stage = "PROGRESS_45";
            nextUpdateAt = exchange.getStartedAt().plus(Exchange.PROGRESS_INTERVAL.multipliedBy(2));
        } else {
            stage = "STARTED";
            nextUpdateAt = exchange.getStartedAt().plus(Exchange.PROGRESS_INTERVAL);
        }
        return new ExchangeSummaryResponse(exchange.getId(), exchange.getAmountXof(), exchange.getAchievedRate(),
                exchange.getAmountCny(), exchange.getStatus(), stage, exchange.getStartedAt(), deadlineAt,
                nextUpdateAt, exchange.getCompletedAt());
    }
}
