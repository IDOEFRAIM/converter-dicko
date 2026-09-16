package com.converter.achievement.service;

import com.converter.achievement.dto.AchievementSummaryResponse;
import com.converter.notification.domain.Notification;
import com.converter.notification.domain.NotificationType;
import com.converter.notification.repository.NotificationRepository;
import com.converter.order.dto.OrderDetailResponse;
import com.converter.quote.dto.QuoteResponse;
import com.converter.support.AbstractOrderPipelineIT;
import com.converter.user.domain.ExperienceProfile;
import com.converter.user.domain.RoleCode;
import com.converter.user.domain.User;
import com.converter.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "Mes gains" (Lot 2) : PRO ne recoit jamais de badge, STUDENT_MALE/FEMALE progressent a travers
 * des paliers distincts, tout derive des ordres COMPLETED deja en base.
 */
class AchievementServiceIT extends AbstractOrderPipelineIT {

    @Autowired
    private AchievementService achievementService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    private User withProfile(User user, ExperienceProfile profile) {
        user.changeExperienceProfile(profile);
        return userRepository.saveAndFlush(user);
    }

    private UUID completeOneOrder(String adminToken, String userToken) {
        QuoteResponse quote = createAcceptedQuote(userToken, "100000");
        OrderDetailResponse order = createOrder(userToken, quote.id(), alipayBeneficiary());
        return completeOrder(adminToken, userToken, order.id(), "100000", "ref-" + UUID.randomUUID(),
                "settlement-" + UUID.randomUUID());
    }

    private List<Notification> badgeUnlockNotifications(UUID userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, Pageable.unpaged())
                .getContent().stream()
                .filter(notification -> notification.getType() == NotificationType.BADGE_UNLOCKED)
                .toList();
    }

    @Test
    void summary_proWithNoOrders_hasNoBadgeAndZeroActivity() {
        User user = createUser(RoleCode.USER);

        AchievementSummaryResponse summary = achievementService.summary(user.getId());

        assertThat(summary.experienceProfile()).isEqualTo(ExperienceProfile.PRO);
        assertThat(summary.completedTransferCount()).isZero();
        assertThat(summary.xp()).isZero();
        assertThat(summary.badgeCode()).isNull();
        assertThat(summary.badgeLabel()).isNull();
        assertThat(summary.nextBadgeLabel()).isNull();
    }

    @Test
    void summary_proWithCompletedOrder_countsActivityButNeverAssignsABadge() {
        String admin = adminToken();
        resetMarginToZero();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        User user = createUser(RoleCode.USER);
        String userToken = tokenFor(user);

        completeOneOrder(admin, userToken);

        AchievementSummaryResponse summary = achievementService.summary(user.getId());

        assertThat(summary.completedTransferCount()).isEqualTo(1);
        assertThat(summary.totalAmountXofCompleted()).isEqualByComparingTo("100000");
        assertThat(summary.xp()).isEqualTo(100);
        // Jamais de badge pour PRO, quelle que soit l'activite (mission : compteur sobre uniquement).
        assertThat(summary.badgeCode()).isNull();
        assertThat(summary.nextBadgeLabel()).isNull();
    }

    @Test
    void summary_studentMaleFirstCompletedOrder_reachesFirstTier() {
        String admin = adminToken();
        resetMarginToZero();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        User user = withProfile(createUser(RoleCode.USER), ExperienceProfile.STUDENT_MALE);
        String userToken = tokenFor(user);

        completeOneOrder(admin, userToken);

        AchievementSummaryResponse summary = achievementService.summary(user.getId());

        assertThat(summary.badgeCode()).isEqualTo("DEBUTANT");
        assertThat(summary.nextBadgeLabel()).isEqualTo("Habitue");
        assertThat(summary.transfersUntilNextBadge()).isEqualTo(4);
    }

    @Test
    void summary_studentFemaleFirstCompletedOrder_reachesTheSameFirstTier() {
        String admin = adminToken();
        resetMarginToZero();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        User user = withProfile(createUser(RoleCode.USER), ExperienceProfile.STUDENT_FEMALE);
        String userToken = tokenFor(user);

        completeOneOrder(admin, userToken);

        AchievementSummaryResponse summary = achievementService.summary(user.getId());

        assertThat(summary.badgeCode()).isEqualTo("DEBUTANT");
        assertThat(summary.nextBadgeLabel()).isEqualTo("Habitue");
    }

    // Le comportement au palier maximal (plus de badge suivant) et chaque frontiere de palier sont
    // verifies a moindre cout par AchievementServiceTest (logique pure, sans HTTP ni base) — inutile
    // de rejouer 30 fois le pipeline complet ici seulement pour ca.

    @Test
    void summary_isolatesActivityPerUser() {
        String admin = adminToken();
        resetMarginToZero();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        User userA = createUser(RoleCode.USER);
        User userB = createUser(RoleCode.USER);

        completeOneOrder(admin, tokenFor(userA));

        assertThat(achievementService.summary(userA.getId()).completedTransferCount()).isEqualTo(1);
        assertThat(achievementService.summary(userB.getId()).completedTransferCount()).isZero();
    }

    /**
     * Mission "differenciation marketing" : celebrer le franchissement d'un palier au moment ou
     * il se produit (voir {@link AchievementService#checkBadgeUnlock}), pas seulement l'afficher
     * au prochain chargement de "Mes gains". Declenche depuis {@code OrderService#transitionToCompleted}
     * : {@code completeOneOrder} suffit donc a l'exercer, sans appel direct.
     */
    @Test
    void checkBadgeUnlock_firesNotification_onFirstCompletedOrder() {
        String admin = adminToken();
        resetMarginToZero();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        User user = withProfile(createUser(RoleCode.USER), ExperienceProfile.STUDENT_MALE);

        completeOneOrder(admin, tokenFor(user));

        List<Notification> notifications = badgeUnlockNotifications(user.getId());
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getMessage()).contains("Debutant");
    }

    @Test
    void checkBadgeUnlock_doesNotFireAgain_whileStillWithinTheSameTier() {
        String admin = adminToken();
        resetMarginToZero();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        User user = withProfile(createUser(RoleCode.USER), ExperienceProfile.STUDENT_MALE);
        String userToken = tokenFor(user);

        completeOneOrder(admin, userToken); // 1er ordre -> franchit DEBUTANT
        completeOneOrder(admin, userToken); // 2e ordre -> reste DEBUTANT (prochain palier a 5)

        assertThat(badgeUnlockNotifications(user.getId())).hasSize(1);
    }

    @Test
    void checkBadgeUnlock_neverFiresForPro() {
        String admin = adminToken();
        resetMarginToZero();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        User user = createUser(RoleCode.USER); // PRO par defaut

        completeOneOrder(admin, tokenFor(user));

        assertThat(badgeUnlockNotifications(user.getId())).isEmpty();
    }
}
