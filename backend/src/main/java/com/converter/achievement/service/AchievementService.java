package com.converter.achievement.service;

import com.converter.achievement.dto.AchievementSummaryResponse;
import com.converter.common.exception.ResourceNotFoundException;
import com.converter.notification.domain.NotificationType;
import com.converter.notification.service.NotificationService;
import com.converter.order.domain.OrderStatus;
import com.converter.order.repository.OrderRepository;
import com.converter.order.repository.OrderStatusAggregate;
import com.converter.pool.domain.PoolStatus;
import com.converter.pool.repository.PoolParticipantRepository;
import com.converter.user.domain.ExperienceProfile;
import com.converter.user.domain.User;
import com.converter.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * "Mes gains" (mission "differenciation marketing", Lot 2) : une seule lecture agregee, jamais un
 * compteur mutable persiste separement — {@code xp} et le palier de badge sont deriverives a
 * chaque appel a partir des ordres {@code COMPLETED} deja en base (meme discipline que {@code
 * BusinessPaymentReportService} : {@code OrderRepository#aggregateByStatus}, jamais un chargement
 * complet suivi d'une somme cote Java).
 *
 * <p><b>PRO ne recoit jamais de badge</b> (mission : "Uniquement un compteur d'economies
 * mensuelles" pour ce profil, aucune gamification) — voir {@link #toResponse}. Les autres profils
 * progressent a travers une <b>echelle unique</b> ({@link #CHANGER_TIERS}), au vocabulaire du
 * metier du change (Cambiste -> Courtier -> Negociant -> Maison de change), jamais un lexique
 * de jeu.
 *
 * <p>Contrairement a {@code BusinessPaymentReportService}, cet endpoint n'est jamais reserve a un
 * sous-ensemble d'utilisateurs (pas de 404 conditionnel) : tout compte authentifie a des gains,
 * memes nuls.
 */
@Service
public class AchievementService {

    private static final Logger log = LoggerFactory.getLogger(AchievementService.class);

    /** XP = nombre d'ordres COMPLETED * ce facteur — formule volontairement simple et transparente,
     * jamais une valeur mutable stockee separement (voir la Javadoc de classe). */
    private static final long XP_PER_COMPLETED_TRANSFER = 100;

    /** Bonus XP par Ruee collective reussie (mission "differenciation marketing", Lot 3) — meme
     * discipline "purement derive" : {@code poolsSucceededCount} est un COMPTE reel de lignes
     * {@code pool_participants} deja persistees, jamais un compteur XP mutable a part. */
    private static final long XP_PER_SUCCEEDED_POOL = 250;

    /** Echelle unique de paliers pour les profils non-PRO — vocabulaire du metier du change,
     * jamais un lexique de jeu (guerrier, legende...). {@code threshold} = nombre minimal
     * d'ordres COMPLETED pour atteindre le palier. */
    private static final List<BadgeTier> CHANGER_TIERS = List.of(
            new BadgeTier(1, "CAMBISTE", "Cambiste"),
            new BadgeTier(5, "COURTIER", "Courtier"),
            new BadgeTier(15, "NEGOCIANT", "Negociant"),
            new BadgeTier(30, "MAISON_DE_CHANGE", "Maison de change"));

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final PoolParticipantRepository poolParticipantRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    public AchievementService(OrderRepository orderRepository, UserRepository userRepository,
                              PoolParticipantRepository poolParticipantRepository,
                              NotificationService notificationService, Clock clock) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.poolParticipantRepository = poolParticipantRepository;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AchievementSummaryResponse summary(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> ResourceNotFoundException.user(userId));

        List<OrderStatusAggregate> allTime = orderRepository.aggregateByStatus(userId, false, null, false, null);
        OrderStatusAggregate completedAllTime = rowFor(allTime, OrderStatus.COMPLETED);
        long completedCount = completedAllTime == null ? 0 : completedAllTime.count();
        BigDecimal totalAmountXof = completedAllTime == null ? BigDecimal.ZERO : completedAllTime.totalAmountXof();

        Instant startOfMonth = LocalDate.now(clock).withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<OrderStatusAggregate> currentMonth = orderRepository.aggregateByStatus(
                userId, true, startOfMonth, false, null);
        OrderStatusAggregate completedThisMonth = rowFor(currentMonth, OrderStatus.COMPLETED);
        BigDecimal currentMonthAmountXof = completedThisMonth == null
                ? BigDecimal.ZERO
                : completedThisMonth.totalAmountXof();

        long poolsSucceededCount = poolParticipantRepository.countByUserIdAndPoolStatus(userId, PoolStatus.SUCCEEDED);

        return toResponse(user.getExperienceProfile(), completedCount, totalAmountXof, currentMonthAmountXof,
                poolsSucceededCount);
    }

    /**
     * Celebre le franchissement d'un nouveau palier de badge (mission "differenciation
     * marketing" : le moment doit etre ressenti, pas seulement decouvert au prochain chargement
     * de "Mes gains"). Appele par {@link com.converter.order.service.OrderService} juste apres
     * qu'un ordre a transitionne vers {@code COMPLETED} <b>dans la meme transaction</b> — cet
     * ordre est donc deja compte dans {@code completedCount} ; le palier "avant" se deduit par
     * simple soustraction (un seul ordre vient de passer COMPLETED, jamais une deuxieme lecture a
     * un instant different qui exposerait a une race).
     *
     * <p>PRO n'a jamais de badge ({@link #resolveBadgeProgress}) : ce mecanisme ne se declenche
     * donc jamais pour ce profil, sans aucun cas particulier necessaire ici. Idempotent en
     * pratique : si le palier n'a pas change (ex. 2e ordre alors que le prochain palier est a 5),
     * aucune notification n'est creee.
     *
     * <p>Appele depuis la transaction de {@code OrderService#transitionToCompleted} : un incident
     * ici (lecture, calcul) est absorbe (journalise) plutot que propage, pour ne jamais faire
     * echouer la transition d'ordre elle-meme — meme discipline que {@code
     * NotificationService#create}, dont l'ecriture est de toute façon deja isolee dans sa propre
     * transaction {@code REQUIRES_NEW}.
     */
    @Transactional
    public void checkBadgeUnlock(UUID userId) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                return;
            }
            ExperienceProfile profile = user.getExperienceProfile();
            if (profile == ExperienceProfile.PRO) {
                return;
            }

            List<OrderStatusAggregate> allTime = orderRepository.aggregateByStatus(userId, false, null, false, null);
            OrderStatusAggregate completedAllTime = rowFor(allTime, OrderStatus.COMPLETED);
            long completedCount = completedAllTime == null ? 0 : completedAllTime.count();
            if (completedCount == 0) {
                return;
            }

            BadgeProgress after = resolveBadgeProgress(profile, completedCount);
            BadgeProgress before = resolveBadgeProgress(profile, completedCount - 1);
            if (after.code() != null && !after.code().equals(before.code())) {
                notificationService.create(userId, NotificationType.BADGE_UNLOCKED, "Nouveau rang debloque !",
                        "Felicitations, tu es maintenant " + after.label() + " !");
            }
        } catch (RuntimeException ex) {
            log.error("Echec de la verification de franchissement de palier de badge pour l'utilisateur {}",
                    userId, ex);
        }
    }

    private AchievementSummaryResponse toResponse(ExperienceProfile profile, long completedCount,
                                                  BigDecimal totalAmountXof, BigDecimal currentMonthAmountXof,
                                                  long poolsSucceededCount) {
        long xp = completedCount * XP_PER_COMPLETED_TRANSFER + poolsSucceededCount * XP_PER_SUCCEEDED_POOL;
        BadgeProgress progress = resolveBadgeProgress(profile, completedCount);
        return new AchievementSummaryResponse(profile, completedCount, totalAmountXof, currentMonthAmountXof,
                poolsSucceededCount, xp, progress.code(), progress.label(), progress.nextLabel(),
                progress.transfersUntilNext(), progress.currentThreshold(), progress.nextThreshold());
    }

    /**
     * Pure, sans dependance (testee unitairement dans {@code AchievementServiceTest}, sans
     * contexte Spring ni base) : {@code PRO} n'a jamais de badge (mission : compteur sobre
     * uniquement) ; tout autre profil progresse a travers la meme echelle {@link #CHANGER_TIERS}.
     */
    static BadgeProgress resolveBadgeProgress(ExperienceProfile profile, long completedCount) {
        if (profile == ExperienceProfile.PRO) {
            return new BadgeProgress(null, null, null, null, null, null);
        }

        BadgeTier current = null;
        BadgeTier next = null;
        for (BadgeTier tier : CHANGER_TIERS) {
            if (completedCount >= tier.threshold()) {
                current = tier;
            } else if (next == null) {
                next = tier;
            }
        }

        Long transfersUntilNext = next == null ? null : next.threshold() - completedCount;
        return new BadgeProgress(
                current == null ? null : current.code(),
                current == null ? null : current.label(),
                next == null ? null : next.label(),
                transfersUntilNext,
                current == null ? null : current.threshold(),
                next == null ? null : next.threshold());
    }

    private static OrderStatusAggregate rowFor(List<OrderStatusAggregate> rows, OrderStatus status) {
        return rows.stream().filter(row -> row.status() == status).findFirst().orElse(null);
    }

    /** Palier de badge : {@code threshold} = nombre minimal d'ordres COMPLETED pour l'atteindre. */
    private record BadgeTier(long threshold, String code, String label) {
    }

    /** Resultat pur de {@link #resolveBadgeProgress} — jamais expose directement, voir {@link #toResponse}. */
    record BadgeProgress(String code, String label, String nextLabel, Long transfersUntilNext,
                         Long currentThreshold, Long nextThreshold) {
    }
}
