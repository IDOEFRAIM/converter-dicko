package com.converter.quote.service;

import com.converter.audit.domain.AuditAction;
import com.converter.audit.service.AuditService;
import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.notification.domain.NotificationType;
import com.converter.notification.service.NotificationService;
import com.converter.quote.domain.Quote;
import com.converter.quote.domain.QuoteDirection;
import com.converter.quote.dto.CreateQuoteRequest;
import com.converter.quote.dto.QuoteResponse;
import com.converter.quote.repository.QuoteRepository;
import com.converter.rate.domain.MarketRate;
import com.converter.rate.engine.AmountBasis;
import com.converter.rate.engine.PricingResult;
import com.converter.rate.engine.RateEngine;
import com.converter.rate.provider.RateProvider;
import com.converter.security.OwnershipService;
import com.converter.settings.domain.SettingKey;
import com.converter.settings.service.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Orchestration du parcours devis : intention client -> RateProvider ->
 * RateEngine -> snapshot immuable -> transitions controlees.
 *
 * <p>Aucune borne min/max de montant n'est appliquee ici : ces bornes
 * (deja definies dans {@code system_settings} en Phase 2,
 * {@code MIN_ORDER_AMOUNT_CFA}/{@code MAX_ORDER_AMOUNT_CFA}) sont
 * conceptuellement liees a la creation d'un <b>ordre</b>, pas d'un
 * devis — un devis reste une simple estimation tarifaire tant qu'il
 * n'est pas transforme en engagement. Leur application sera cablee
 * lors de la reconstruction du module {@code order} (Phase 4).
 */
@Service
public class QuoteService {

    private static final Logger log = LoggerFactory.getLogger(QuoteService.class);

    private final QuoteRepository quoteRepository;
    private final RateProvider rateProvider;
    private final RateEngine rateEngine;
    private final SettingsService settingsService;
    private final OwnershipService ownershipService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final Clock clock;

    public QuoteService(QuoteRepository quoteRepository,
                        RateProvider rateProvider,
                        RateEngine rateEngine,
                        SettingsService settingsService,
                        OwnershipService ownershipService,
                        AuditService auditService,
                        NotificationService notificationService,
                        Clock clock) {
        this.quoteRepository = quoteRepository;
        this.rateProvider = rateProvider;
        this.rateEngine = rateEngine;
        this.settingsService = settingsService;
        this.ownershipService = ownershipService;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    /**
     * Cree un devis, snapshot financier immuable.
     *
     * <p>La lecture de la cotation courante ({@link RateProvider#currentRate})
     * est verrouillee (voir {@code RateSourceRepository#findCurrentForPricing})
     * et se deroule dans <b>cette meme transaction</b> : si une
     * publication administrative est en cours au meme instant, cette
     * transaction attend qu'elle se termine avant de lire, ou la bloque
     * jusqu'a son propre commit — dans tous les cas, le devis cree
     * reflete toujours une cotation qui etait reellement courante au
     * moment ou la transaction s'est validee, jamais une valeur
     * entre-deux incoherente.
     */
    @Transactional
    public QuoteResponse create(CreateQuoteRequest request, UUID userId) {
        AmountBasis basis = toAmountBasis(request);
        BigDecimal amount = extractAmount(request, basis);

        MarketRate marketRate = rateProvider.currentRate(RateProvider.DEFAULT_CURRENCY_PAIR);
        BigDecimal marginPercentage = settingsService.getDecimal(SettingKey.DEFAULT_MARGIN_PERCENTAGE);
        BigDecimal feePercentage = settingsService.getDecimal(SettingKey.DEFAULT_FEE_PERCENTAGE);
        BigDecimal fixedFeeXof = settingsService.getDecimal(SettingKey.DEFAULT_FIXED_FEE_XOF);

        PricingResult pricing = rateEngine.price(basis, amount, marketRate, marginPercentage, feePercentage, fixedFeeXof);

        Instant now = clock.instant();
        Duration validity = settingsService.getMinutes(SettingKey.RATE_LOCK_DURATION_MINUTES);
        Quote quote = new Quote(userId, request.direction(), pricing, marketRate.rateSourceId(), now, now.plus(validity));
        Quote saved = quoteRepository.save(quote);

        auditService.record(userId, null, AuditAction.QUOTE_CREATED, "Quote", saved.getId().toString(), null);
        notificationService.create(userId, NotificationType.QUOTE_CREATED, "Devis cree",
                "Votre devis " + request.direction() + " a ete cree.");
        log.info("Devis {} cree pour {} ({} {})", saved.getId(), userId, request.direction(), amount);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public QuoteResponse get(UUID id, UUID userId) {
        Quote quote = quoteRepository.findById(id)
                .orElseThrow(() -> notFound(id));
        ownershipService.assertOwnedBy(quote.getUserId(), userId, ErrorCode.QUOTE_NOT_FOUND,
                "Devis introuvable : " + id);
        return toResponse(quote);
    }

    @Transactional
    public QuoteResponse accept(UUID id, UUID userId) {
        Quote quote = quoteRepository.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
        ownershipService.assertOwnedBy(quote.getUserId(), userId, ErrorCode.QUOTE_NOT_FOUND,
                "Devis introuvable : " + id);

        quote.accept(clock.instant());

        auditService.record(userId, null, AuditAction.QUOTE_ACCEPTED, "Quote", quote.getId().toString(), null);
        log.info("Devis {} accepte par {}", quote.getId(), userId);

        return toResponse(quote);
    }

    @Transactional
    public QuoteResponse cancel(UUID id, UUID userId) {
        Quote quote = quoteRepository.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
        ownershipService.assertOwnedBy(quote.getUserId(), userId, ErrorCode.QUOTE_NOT_FOUND,
                "Devis introuvable : " + id);

        quote.cancel(clock.instant());

        auditService.record(userId, null, AuditAction.QUOTE_CANCELLED, "Quote", quote.getId().toString(), null);
        log.info("Devis {} annule par {}", quote.getId(), userId);

        return toResponse(quote);
    }

    // -----------------------------------------------------------------

    private AmountBasis toAmountBasis(CreateQuoteRequest request) {
        return switch (request.direction()) {
            case SEND_XOF -> AmountBasis.XOF;
            case RECEIVE_CNY -> AmountBasis.CNY;
        };
    }

    private BigDecimal extractAmount(CreateQuoteRequest request, AmountBasis basis) {
        return switch (basis) {
            case XOF -> {
                if (request.amountXof() == null) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                            "amountXof est obligatoire pour la direction SEND_XOF.");
                }
                if (request.amountCny() != null) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                            "amountCny ne doit pas etre fourni pour la direction SEND_XOF.");
                }
                yield request.amountXof();
            }
            case CNY -> {
                if (request.amountCny() == null) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                            "amountCny est obligatoire pour la direction RECEIVE_CNY.");
                }
                if (request.amountXof() != null) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                            "amountXof ne doit pas etre fourni pour la direction RECEIVE_CNY.");
                }
                yield request.amountCny();
            }
        };
    }

    private BusinessException notFound(UUID id) {
        return new BusinessException(ErrorCode.QUOTE_NOT_FOUND, "Devis introuvable : " + id);
    }

    private QuoteResponse toResponse(Quote quote) {
        QuoteDirection direction = quote.getDirection();
        return new QuoteResponse(
                quote.getId(),
                direction,
                quote.getAmountXof(),
                quote.getAmountCny(),
                quote.getCustomerRate(),
                quote.getFeeXof(),
                quote.getNetAmountXof(),
                quote.effectiveStatus(clock.instant()),
                quote.getCreatedAt(),
                quote.getExpiresAt());
    }
}
