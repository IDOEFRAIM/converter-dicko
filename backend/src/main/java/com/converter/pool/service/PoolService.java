package com.converter.pool.service;

import com.converter.audit.domain.AuditAction;
import com.converter.audit.service.AuditService;
import com.converter.common.api.PageResponse;
import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.notification.domain.NotificationType;
import com.converter.notification.service.NotificationService;
import com.converter.pool.domain.Pool;
import com.converter.pool.domain.PoolParticipant;
import com.converter.pool.domain.PoolStatus;
import com.converter.pool.dto.CreatePoolRequest;
import com.converter.pool.dto.PoolParticipantResponse;
import com.converter.pool.dto.PoolResponse;
import com.converter.pool.repository.PoolParticipantRepository;
import com.converter.pool.repository.PoolRepository;
import com.converter.rate.provider.RateProvider;
import com.converter.security.OwnershipService;
import com.converter.settings.domain.SettingKey;
import com.converter.settings.service.SettingsService;
import com.converter.user.domain.User;
import com.converter.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * "Ruee collective" (mission "differenciation marketing", Lot 3) : creation, invitation par code
 * court, contribution (voir {@code OrderService#create}), declenchement de la recompense a
 * l'objectif, et les deux transitions pilotees par {@link com.converter.pool.scheduler.PoolScheduler}
 * -- expiration et annulation par le createur.
 *
 * <p><b>Jamais de re-pricing retroactif</b> : la recompense accordee a la reussite s'applique
 * exclusivement au PROCHAIN devis de chaque participant (consommee par {@code QuoteService}) --
 * jamais a l'ordre qui a rempli l'objectif, dont le pricing reste fige pour toujours (meme
 * garantie structurelle que {@code Order}/{@code Quote} partout ailleurs dans ce backend).
 */
@Service
public class PoolService {

    private static final Logger log = LoggerFactory.getLogger(PoolService.class);

    /** Alphabet sans caracteres ambigus (pas de 0/O ni 1/I) -- un code se lit et se retape a la main. */
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;
    private static final int MAX_CODE_GENERATION_ATTEMPTS = 8;

    private final PoolRepository poolRepository;
    private final PoolParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final SettingsService settingsService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final OwnershipService ownershipService;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public PoolService(PoolRepository poolRepository,
                       PoolParticipantRepository participantRepository,
                       UserRepository userRepository,
                       SettingsService settingsService,
                       NotificationService notificationService,
                       AuditService auditService,
                       OwnershipService ownershipService,
                       Clock clock) {
        this.poolRepository = poolRepository;
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
        this.settingsService = settingsService;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.ownershipService = ownershipService;
        this.clock = clock;
    }

    @Transactional
    public PoolResponse create(CreatePoolRequest request, UUID userId) {
        Instant now = clock.instant();
        BigDecimal rewardPercentage = settingsService.getDecimal(SettingKey.POOL_REWARD_MARGIN_REDUCTION_PERCENTAGE);

        Pool pool = new Pool(generateUniqueCode(), userId, RateProvider.DEFAULT_CURRENCY_PAIR,
                request.targetAmountXof(), rewardPercentage, now, now.plus(Duration.ofMinutes(request.durationMinutes())));
        Pool saved = poolRepository.save(pool);

        participantRepository.save(new PoolParticipant(saved.getId(), userId, true, now));

        auditService.record(userId, null, AuditAction.POOL_CREATED, "Pool", saved.getId().toString(),
                "{\"targetAmountXof\":\"" + request.targetAmountXof() + "\",\"code\":\"" + saved.getCode() + "\"}");
        log.info("Ruee {} ({}) creee par {} : objectif {} XOF, echeance {}", saved.getId(), saved.getCode(), userId,
                request.targetAmountXof(), saved.getExpiresAt());

        return toResponse(saved, userId, 1);
    }

    @Transactional(readOnly = true)
    public PoolResponse get(UUID id, UUID viewerId) {
        Pool pool = poolRepository.findById(id).orElseThrow(() -> notFound(id.toString()));
        return toResponse(pool, viewerId, (int) participantRepository.countByPoolId(pool.getId()));
    }

    @Transactional(readOnly = true)
    public PoolResponse getByCode(String code, UUID viewerId) {
        Pool pool = poolRepository.findByCode(code.toUpperCase()).orElseThrow(() -> notFound(code));
        return toResponse(pool, viewerId, (int) participantRepository.countByPoolId(pool.getId()));
    }

    @Transactional(readOnly = true)
    public List<PoolParticipantResponse> participants(UUID poolId) {
        // 404 implicite si le pool n'existe pas : une liste vide serait ambigue (pool inexistant
        // vs pool sans participants, qui ne devrait de toute facon jamais arriver).
        if (!poolRepository.existsById(poolId)) {
            throw notFound(poolId.toString());
        }
        List<PoolParticipant> rows = participantRepository.findByPoolIdOrderByJoinedAtAsc(poolId);
        Map<UUID, String> firstNames = userRepository.findAllById(rows.stream().map(PoolParticipant::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, User::getFirstName));
        return rows.stream()
                .map(p -> new PoolParticipantResponse(p.getUserId(), firstNames.getOrDefault(p.getUserId(), "?"),
                        p.isCreator(), p.getJoinedAt(), p.getContributedAmountXof()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<PoolResponse> mine(UUID userId, Pageable pageable) {
        Page<PoolParticipant> page = participantRepository.findByUserId(userId,
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()));
        List<UUID> poolIds = page.getContent().stream().map(PoolParticipant::getPoolId).toList();
        Map<UUID, Pool> poolsById = poolRepository.findAllById(poolIds).stream()
                .collect(Collectors.toMap(Pool::getId, p -> p));
        return PageResponse.from(page, participant -> {
            Pool pool = poolsById.get(participant.getPoolId());
            return toResponse(pool, userId, (int) participantRepository.countByPoolId(pool.getId()));
        });
    }

    @Transactional
    public PoolResponse join(UUID poolId, UUID userId) {
        Pool pool = poolRepository.findById(poolId).orElseThrow(() -> notFound(poolId.toString()));
        assertJoinable(pool);

        if (participantRepository.findByPoolIdAndUserId(poolId, userId).isPresent()) {
            throw new BusinessException(ErrorCode.POOL_ALREADY_JOINED, "Vous avez deja rejoint cette Ruee.");
        }

        participantRepository.save(new PoolParticipant(poolId, userId, false, clock.instant()));
        auditService.record(userId, null, AuditAction.POOL_JOINED, "Pool", poolId.toString(), null);
        log.info("Utilisateur {} a rejoint la Ruee {}", userId, poolId);

        return toResponse(pool, userId, (int) participantRepository.countByPoolId(poolId));
    }

    @Transactional
    public PoolResponse cancel(UUID poolId, UUID userId) {
        Pool pool = poolRepository.findByIdForUpdate(poolId).orElseThrow(() -> notFound(poolId.toString()));
        ownershipService.assertOwnedBy(pool.getCreatorId(), userId, ErrorCode.POOL_NOT_FOUND,
                "Ruee introuvable : " + poolId);

        Instant now = clock.instant();
        pool.cancel(now);
        Pool saved = poolRepository.save(pool);

        auditService.record(userId, null, AuditAction.POOL_CANCELLED, "Pool", poolId.toString(), null);
        notifyParticipants(poolId, userId, NotificationType.POOL_EXPIRED,
                "Ruee annulee", "La Ruee que vous avez rejointe a ete annulee par son createur.");
        log.info("Ruee {} annulee par son createur {}", poolId, userId);

        return toResponse(saved, userId, (int) participantRepository.countByPoolId(poolId));
    }

    // -----------------------------------------------------------------
    // Contribution (appelee par OrderService#create) et scheduler d'expiration.
    // -----------------------------------------------------------------

    /**
     * Enregistre la contribution d'un ordre venant d'etre cree, et declenche la recompense de
     * TOUS les participants si l'objectif est atteint. Appelee dans la MEME transaction que la
     * creation de l'ordre (voir {@code OrderService#create}) -- si cette methode leve, la creation
     * de l'ordre est integralement annulee plutot que de laisser un ordre "orphelin" d'un pool
     * refuse.
     */
    @Transactional
    public void recordContribution(UUID poolId, UUID userId, BigDecimal amountXof) {
        Pool pool = poolRepository.findByIdForUpdate(poolId).orElseThrow(() -> notFound(poolId.toString()));
        Instant now = clock.instant();
        if (pool.getStatus() != PoolStatus.ACTIVE || pool.isPastDeadline(now)) {
            throw new BusinessException(ErrorCode.POOL_INACTIVE,
                    "Cette Ruee n'est plus active, votre ordre ne peut plus y contribuer.");
        }

        PoolParticipant participant = participantRepository.findByPoolIdAndUserId(poolId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POOL_NOT_JOINED,
                        "Vous devez rejoindre cette Ruee avant d'y contribuer."));

        participant.addContribution(amountXof);
        participantRepository.save(participant);

        pool.contribute(amountXof, now);

        if (pool.hasReachedTarget()) {
            pool.succeed(now);
            grantRewardToAllParticipants(pool, now);
            auditService.recordSystem(AuditAction.POOL_SUCCEEDED, "Pool", poolId.toString(),
                    "{\"currentAmountXof\":\"" + pool.getCurrentAmountXof() + "\"}");
            log.info("Ruee {} reussie : objectif {} atteint ({} XOF cumules)", poolId, pool.getTargetAmountXof(),
                    pool.getCurrentAmountXof());
        }
        poolRepository.save(pool);
    }

    private void grantRewardToAllParticipants(Pool pool, Instant now) {
        List<PoolParticipant> allParticipants = participantRepository.findByPoolIdOrderByJoinedAtAsc(pool.getId());
        for (PoolParticipant participant : allParticipants) {
            participant.grantReward(pool.getRewardMarginReductionPercentage(), now);
            participantRepository.save(participant);
        }
        notifyParticipants(pool.getId(), null, NotificationType.POOL_SUCCEEDED,
                "Objectif atteint !",
                "Votre Ruee a atteint son objectif de " + pool.getTargetAmountXof() + " XOF. Vous beneficiez de -"
                        + pool.getRewardMarginReductionPercentage() + " points de marge sur votre prochain transfert.");
    }

    /** Pilote par {@code PoolScheduler} -- une transaction par pool, verrou pris a l'interieur. */
    @Transactional(readOnly = true)
    public List<UUID> activePoolIds() {
        return poolRepository.findIdsByStatus(PoolStatus.ACTIVE);
    }

    @Transactional
    public void processExpiration(UUID poolId) {
        Pool pool = poolRepository.findByIdForUpdate(poolId).orElse(null);
        if (pool == null || pool.getStatus() != PoolStatus.ACTIVE) {
            return;
        }
        Instant now = clock.instant();
        if (!pool.isPastDeadline(now)) {
            return;
        }
        pool.expire(now);
        poolRepository.save(pool);

        notifyParticipants(poolId, null, NotificationType.POOL_EXPIRED,
                "Ruee expiree",
                "Votre Ruee n'a pas atteint son objectif de " + pool.getTargetAmountXof() + " XOF avant l'echeance.");
        log.info("Ruee {} expiree (objectif {} non atteint, {} XOF cumules)", poolId, pool.getTargetAmountXof(),
                pool.getCurrentAmountXof());
    }

    // -----------------------------------------------------------------

    private void assertJoinable(Pool pool) {
        if (pool.getStatus() != PoolStatus.ACTIVE || pool.isPastDeadline(clock.instant())) {
            throw new BusinessException(ErrorCode.POOL_INACTIVE,
                    "Cette Ruee n'est plus active (statut actuel : " + pool.getStatus() + ").");
        }
    }

    private void notifyParticipants(UUID poolId, UUID excludeUserId, NotificationType type, String title,
                                    String message) {
        for (PoolParticipant participant : participantRepository.findByPoolIdOrderByJoinedAtAsc(poolId)) {
            if (participant.getUserId().equals(excludeUserId)) {
                continue;
            }
            notificationService.create(participant.getUserId(), type, title, message);
        }
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
            String candidate = randomCode();
            if (!poolRepository.existsByCode(candidate)) {
                return candidate;
            }
        }
        // Astronomiquement improbable (32^6 combinaisons) -- filet de securite explicite plutot
        // qu'une boucle infinie ou un code silencieusement duplique.
        throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                "Impossible de generer un code de Ruee unique, reessayez.");
    }

    private String randomCode() {
        StringBuilder builder = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            builder.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return builder.toString();
    }

    private BusinessException notFound(String reference) {
        return new BusinessException(ErrorCode.POOL_NOT_FOUND, "Ruee introuvable : " + reference);
    }

    private PoolResponse toResponse(Pool pool, UUID viewerId, int participantCount) {
        boolean viewerIsParticipant = viewerId != null
                && participantRepository.findByPoolIdAndUserId(pool.getId(), viewerId).isPresent();
        return new PoolResponse(
                pool.getId(), pool.getCode(), pool.getCreatorId(), pool.getCurrencyPair(),
                pool.getTargetAmountXof(), pool.getCurrentAmountXof(), pool.getStatus(), participantCount,
                pool.getRewardMarginReductionPercentage(), pool.getCreatedAt(), pool.getExpiresAt(),
                pool.getSucceededAt(), pool.getExpiredAt(), pool.getCancelledAt(),
                viewerIsParticipant, pool.getCreatorId().equals(viewerId));
    }
}
