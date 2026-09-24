package com.converter.pool.service;

import com.converter.common.api.ApiResponse;
import com.converter.common.exception.BusinessException;
import com.converter.notification.domain.Notification;
import com.converter.notification.domain.NotificationType;
import com.converter.notification.repository.NotificationRepository;
import com.converter.order.dto.CreateOrderRequest;
import com.converter.order.dto.OrderDetailResponse;
import com.converter.pool.domain.Pool;
import com.converter.pool.domain.PoolStatus;
import com.converter.pool.dto.CreatePoolRequest;
import com.converter.pool.dto.PoolParticipantResponse;
import com.converter.pool.dto.PoolResponse;
import com.converter.pool.repository.PoolParticipantRepository;
import com.converter.pool.repository.PoolRepository;
import com.converter.quote.domain.QuoteDirection;
import com.converter.quote.dto.CreateQuoteRequest;
import com.converter.quote.dto.QuoteResponse;
import com.converter.quote.service.QuoteService;
import com.converter.support.AbstractOrderPipelineIT;
import com.converter.user.domain.RoleCode;
import com.converter.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * "Ruee collective" (mission "differenciation marketing", Lot 3) : creation, invitation par code,
 * contribution (via une creation d'ordre reelle), recompense de TOUS les participants a la
 * reussite (jamais retroactive), et consommation unique sur le prochain devis.
 */
class PoolServiceIT extends AbstractOrderPipelineIT {

    @Autowired
    private PoolService poolService;

    @Autowired
    private QuoteService quoteService;

    @Autowired
    private PoolRepository poolRepository;

    @Autowired
    private PoolParticipantRepository poolParticipantRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    private void seedPricing(String admin) {
        resetMarginToZero();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
    }

    private UUID createOrderForPool(String userToken, UUID poolId, String amountXof) {
        QuoteResponse quote = createAcceptedQuote(userToken, amountXof);
        ResponseEntity<ApiResponse<OrderDetailResponse>> response = restTemplate.exchange(
                "/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>(new CreateOrderRequest(quote.id(), alipayBeneficiary(), "pool-test", null, null,
                        null, poolId), auth(userToken)),
                new ParameterizedTypeReference<ApiResponse<OrderDetailResponse>>() {
                });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().data().id();
    }

    // ---- Creation et invitation ----

    @Test
    void create_autoJoinsCreatorAsFirstParticipant() {
        User creator = createUser(RoleCode.USER);

        PoolResponse pool = poolService.create(new CreatePoolRequest(new BigDecimal("1000000"), 30), creator.getId());

        assertThat(pool.status()).isEqualTo(PoolStatus.ACTIVE);
        assertThat(pool.participantCount()).isEqualTo(1);
        assertThat(pool.viewerIsCreator()).isTrue();
        assertThat(pool.viewerIsParticipant()).isTrue();
        assertThat(pool.code()).hasSize(6);

        List<PoolParticipantResponse> participants = poolService.participants(pool.id());
        assertThat(participants).hasSize(1);
        assertThat(participants.get(0).isCreator()).isTrue();
    }

    @Test
    void getByCode_isCaseInsensitiveAndVisibleToNonParticipants() {
        User creator = createUser(RoleCode.USER);
        User stranger = createUser(RoleCode.USER);
        PoolResponse pool = poolService.create(new CreatePoolRequest(new BigDecimal("1000000"), 30), creator.getId());

        PoolResponse viewed = poolService.getByCode(pool.code().toLowerCase(), stranger.getId());

        assertThat(viewed.id()).isEqualTo(pool.id());
        assertThat(viewed.viewerIsParticipant()).isFalse();
        assertThat(viewed.viewerIsCreator()).isFalse();
    }

    @Test
    void join_addsParticipant() {
        User creator = createUser(RoleCode.USER);
        User friend = createUser(RoleCode.USER);
        PoolResponse pool = poolService.create(new CreatePoolRequest(new BigDecimal("1000000"), 30), creator.getId());

        PoolResponse joined = poolService.join(pool.id(), friend.getId());

        assertThat(joined.participantCount()).isEqualTo(2);
        assertThat(joined.viewerIsParticipant()).isTrue();
        assertThat(joined.viewerIsCreator()).isFalse();
    }

    @Test
    void join_twice_throwsAlreadyJoined() {
        User creator = createUser(RoleCode.USER);
        User friend = createUser(RoleCode.USER);
        PoolResponse pool = poolService.create(new CreatePoolRequest(new BigDecimal("1000000"), 30), creator.getId());
        poolService.join(pool.id(), friend.getId());

        assertThatThrownBy(() -> poolService.join(pool.id(), friend.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode().name()).isEqualTo("POOL_ALREADY_JOINED"));
    }

    @Test
    void cancel_byCreator_marksCancelledAndNotifiesParticipants() {
        User creator = createUser(RoleCode.USER);
        User friend = createUser(RoleCode.USER);
        PoolResponse pool = poolService.create(new CreatePoolRequest(new BigDecimal("1000000"), 30), creator.getId());
        poolService.join(pool.id(), friend.getId());

        PoolResponse cancelled = poolService.cancel(pool.id(), creator.getId());

        assertThat(cancelled.status()).isEqualTo(PoolStatus.CANCELLED);
        List<Notification> notifications = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(friend.getId(), org.springframework.data.domain.PageRequest.of(0, 20))
                .getContent();
        assertThat(notifications).extracting(Notification::getType).contains(NotificationType.POOL_EXPIRED);
    }

    @Test
    void cancel_byNonCreator_returns404() {
        User creator = createUser(RoleCode.USER);
        User friend = createUser(RoleCode.USER);
        PoolResponse pool = poolService.create(new CreatePoolRequest(new BigDecimal("1000000"), 30), creator.getId());
        poolService.join(pool.id(), friend.getId());

        assertThatThrownBy(() -> poolService.cancel(pool.id(), friend.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode().name()).isEqualTo("POOL_NOT_FOUND"));
    }

    // ---- Contribution reelle (creation d'ordre) et reussite ----

    @Test
    void contribution_withoutJoining_isRejected() {
        String admin = adminToken();
        seedPricing(admin);
        User creator = createUser(RoleCode.USER);
        User stranger = createUser(RoleCode.USER);
        String strangerToken = tokenFor(stranger);
        PoolResponse pool = poolService.create(new CreatePoolRequest(new BigDecimal("1000000"), 30), creator.getId());

        QuoteResponse quote = createAcceptedQuote(strangerToken, "50000");
        ResponseEntity<ApiResponse<OrderDetailResponse>> response = restTemplate.exchange(
                "/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>(new CreateOrderRequest(quote.id(), alipayBeneficiary(), "test", null, null, null,
                        pool.id()), auth(strangerToken)),
                new ParameterizedTypeReference<ApiResponse<OrderDetailResponse>>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void contribution_reachingTarget_succeedsAndRewardsEveryParticipantIncludingNonContributors() {
        String admin = adminToken();
        seedPricing(admin);
        User creator = createUser(RoleCode.USER);
        User contributor = createUser(RoleCode.USER);
        User silentJoiner = createUser(RoleCode.USER);
        String creatorToken = tokenFor(creator);
        String contributorToken = tokenFor(contributor);

        PoolResponse pool = poolService.create(new CreatePoolRequest(new BigDecimal("150000"), 30), creator.getId());
        poolService.join(pool.id(), contributor.getId());
        poolService.join(pool.id(), silentJoiner.getId());

        // Le createur contribue 100000, encore sous l'objectif de 150000.
        createOrderForPool(creatorToken, pool.id(), "100000");
        PoolResponse afterFirst = poolService.get(pool.id(), creator.getId());
        assertThat(afterFirst.status()).isEqualTo(PoolStatus.ACTIVE);
        assertThat(afterFirst.currentAmountXof()).isEqualByComparingTo("100000");

        // Le contributeur ajoute 60000 : 160000 >= 150000, objectif atteint.
        createOrderForPool(contributorToken, pool.id(), "60000");

        PoolResponse succeeded = poolService.get(pool.id(), creator.getId());
        assertThat(succeeded.status()).isEqualTo(PoolStatus.SUCCEEDED);
        assertThat(succeeded.currentAmountXof()).isEqualByComparingTo("160000");

        // TOUS les participants recoivent la recompense, y compris silentJoiner qui n'a jamais
        // cree d'ordre -- mission : "chaque participant recoit un badge special".
        assertThat(poolParticipantRepository.findByPoolIdAndUserId(pool.id(), creator.getId()).orElseThrow()
                .hasUnconsumedReward()).isTrue();
        assertThat(poolParticipantRepository.findByPoolIdAndUserId(pool.id(), contributor.getId()).orElseThrow()
                .hasUnconsumedReward()).isTrue();
        assertThat(poolParticipantRepository.findByPoolIdAndUserId(pool.id(), silentJoiner.getId()).orElseThrow()
                .hasUnconsumedReward()).isTrue();

        List<Notification> notifications = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(silentJoiner.getId(), org.springframework.data.domain.PageRequest.of(0, 20))
                .getContent();
        assertThat(notifications).extracting(Notification::getType).contains(NotificationType.POOL_SUCCEEDED);
    }

    @Test
    void reward_appliesToNextQuoteOnly_neverRetroactivelyAndNeverTwice() {
        // Volontairement PAS resetMarginToZero() ici : la reduction de recompense est plafonnee a
        // zero (jamais negative) -- avec une marge de base deja a zero, la recompense n'aurait
        // silencieusement aucun effet observable sur le taux, ce qui invaliderait ce test precis.
        // On garde donc la marge par defaut seedee (V8, 1.5%) pour observer un vrai changement.
        String admin = adminToken();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        User creator = createUser(RoleCode.USER);
        String creatorToken = tokenFor(creator);
        PoolResponse pool = poolService.create(new CreatePoolRequest(new BigDecimal("100000"), 30), creator.getId());

        // L'ordre qui remplit l'objectif est cree AVANT que la recompense n'existe : son pricing
        // (fige au moment de son propre devis) ne doit jamais etre modifie retroactivement.
        UUID contributingOrderId = createOrderForPool(creatorToken, pool.id(), "100000");
        OrderDetailResponse contributingOrder = restTemplate.exchange(
                "/api/v1/orders/" + contributingOrderId, HttpMethod.GET, new HttpEntity<>(auth(creatorToken)),
                new ParameterizedTypeReference<ApiResponse<OrderDetailResponse>>() {
                }).getBody().data();
        BigDecimal rateBeforeReward = contributingOrder.customerRate();

        assertThat(poolService.get(pool.id(), creator.getId()).status()).isEqualTo(PoolStatus.SUCCEEDED);

        // Premier devis APRES la reussite : la reduction s'applique.
        QuoteResponse rewardedQuote = quoteService.create(
                new CreateQuoteRequest(QuoteDirection.SEND_XOF, new BigDecimal("50000"), null), creator.getId());
        assertThat(rewardedQuote.poolRewardApplied()).isTrue();
        assertThat(rewardedQuote.customerRate()).isNotEqualByComparingTo(rateBeforeReward);

        // Deuxieme devis : la recompense est deja consommee, jamais reappliquee une seconde fois.
        QuoteResponse secondQuote = quoteService.create(
                new CreateQuoteRequest(QuoteDirection.SEND_XOF, new BigDecimal("50000"), null), creator.getId());
        assertThat(secondQuote.poolRewardApplied()).isFalse();
    }

    // ---- Expiration (scheduler) ----

    @Test
    void processExpiration_pastDeadline_expiresAndNotifiesParticipants() {
        User creator = createUser(RoleCode.USER);
        User friend = createUser(RoleCode.USER);
        Instant past = Instant.now().minusSeconds(3600);
        Pool pool = poolRepository.save(new Pool("TESTEX", creator.getId(), "XOF/CNY", new BigDecimal("1000000"),
                new BigDecimal("0.5"), past, Instant.now().minusSeconds(10)));
        poolParticipantRepository.save(new com.converter.pool.domain.PoolParticipant(pool.getId(), creator.getId(), true, past));
        poolParticipantRepository.save(new com.converter.pool.domain.PoolParticipant(pool.getId(), friend.getId(), false, past));

        poolService.processExpiration(pool.getId());

        Pool reloaded = poolRepository.findById(pool.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PoolStatus.EXPIRED);
        List<Notification> notifications = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(friend.getId(), org.springframework.data.domain.PageRequest.of(0, 20))
                .getContent();
        assertThat(notifications).extracting(Notification::getType).contains(NotificationType.POOL_EXPIRED);
    }

    @Test
    void processExpiration_notYetPastDeadline_staysActive() {
        User creator = createUser(RoleCode.USER);
        PoolResponse pool = poolService.create(new CreatePoolRequest(new BigDecimal("1000000"), 30), creator.getId());

        poolService.processExpiration(pool.id());

        assertThat(poolRepository.findById(pool.id()).orElseThrow().getStatus()).isEqualTo(PoolStatus.ACTIVE);
    }
}
