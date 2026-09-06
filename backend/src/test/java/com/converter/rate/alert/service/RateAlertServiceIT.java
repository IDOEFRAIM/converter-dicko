package com.converter.rate.alert.service;

import com.converter.common.exception.BusinessException;
import com.converter.notification.domain.Notification;
import com.converter.notification.domain.NotificationType;
import com.converter.notification.repository.NotificationRepository;
import com.converter.preferredrate.domain.PreferredRateDirection;
import com.converter.rate.alert.domain.RateAlert;
import com.converter.rate.alert.domain.RateAlertStatus;
import com.converter.rate.alert.domain.RateComparison;
import com.converter.rate.alert.dto.CreateRateAlertRequest;
import com.converter.rate.alert.dto.RateAlertResponse;
import com.converter.rate.alert.repository.RateAlertRepository;
import com.converter.support.AbstractRateQuoteIT;
import com.converter.user.domain.RoleCode;
import com.converter.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 6 — alertes de taux. Verifie la creation/annulation/ownership, l'evaluation par le
 * scheduler (condition, egalite, expiration, absence de taux public), l'absence de double
 * notification (concurrence reelle), et la persistance des transitions (section 37, "redemarrage").
 */
class RateAlertServiceIT extends AbstractRateQuoteIT {

    @Autowired
    private RateAlertService rateAlertService;

    @Autowired
    private RateAlertRepository rateAlertRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    // ---- 1/2/3/4 : creation et validations ----

    @Test
    void create_persistsActiveAlertWithDefaults() {
        User user = createUser(RoleCode.USER);
        RateAlertResponse response = rateAlertService.create(
                new CreateRateAlertRequest(null, null, new BigDecimal("83.50"), null, null), user.getId());

        assertThat(response.status()).isEqualTo(RateAlertStatus.ACTIVE);
        assertThat(response.currencyPair()).isEqualTo("XOF/CNY");
        assertThat(response.direction()).isEqualTo(PreferredRateDirection.XOF_TO_CNY);
        assertThat(response.comparison()).isEqualTo(RateComparison.LESS_THAN_OR_EQUAL);
        assertThat(response.targetRate()).isEqualByComparingTo("83.50");
        assertThat(response.expiresAt()).isNull();
        assertThat(response.triggeredAt()).isNull();
    }

    @Test
    void create_unsupportedPair_throwsValidationError() {
        User user = createUser(RoleCode.USER);
        assertThatThrownBy(() -> rateAlertService.create(
                new CreateRateAlertRequest("USD/EUR", null, new BigDecimal("83.50"), null, null), user.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode().name()).isEqualTo("VALIDATION_ERROR"));
    }

    @Test
    void create_expiresAtInThePast_throwsValidationError() {
        User user = createUser(RoleCode.USER);
        Instant past = Instant.now().minusSeconds(3600);
        assertThatThrownBy(() -> rateAlertService.create(
                new CreateRateAlertRequest(null, null, new BigDecimal("83.50"), null, past), user.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode().name()).isEqualTo("VALIDATION_ERROR"));
    }

    @Test
    void create_expiresAtInTheFuture_isAccepted() {
        User user = createUser(RoleCode.USER);
        Instant future = Instant.now().plusSeconds(3600);
        RateAlertResponse response = rateAlertService.create(
                new CreateRateAlertRequest(null, null, new BigDecimal("83.50"), null, future), user.getId());

        assertThat(response.expiresAt()).isEqualTo(future);
    }

    // ---- 5/8/14 (ownership) ----

    @Test
    void get_anotherUsersAlert_returns404() {
        User owner = createUser(RoleCode.USER);
        User stranger = createUser(RoleCode.USER);
        RateAlertResponse alert = rateAlertService.create(
                new CreateRateAlertRequest(null, null, new BigDecimal("83.50"), null, null), owner.getId());

        assertThatThrownBy(() -> rateAlertService.get(alert.id(), stranger.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode().name()).isEqualTo("RATE_ALERT_NOT_FOUND"));
    }

    @Test
    void cancel_anotherUsersAlert_returns404() {
        User owner = createUser(RoleCode.USER);
        User stranger = createUser(RoleCode.USER);
        RateAlertResponse alert = rateAlertService.create(
                new CreateRateAlertRequest(null, null, new BigDecimal("83.50"), null, null), owner.getId());

        assertThatThrownBy(() -> rateAlertService.cancel(alert.id(), stranger.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode().name()).isEqualTo("RATE_ALERT_NOT_FOUND"));
    }

    // ---- 7 : cancellation ----

    @Test
    void cancel_activeAlert_transitionsToCancelled() {
        User user = createUser(RoleCode.USER);
        RateAlertResponse created = rateAlertService.create(
                new CreateRateAlertRequest(null, null, new BigDecimal("83.50"), null, null), user.getId());

        RateAlertResponse cancelled = rateAlertService.cancel(created.id(), user.getId());

        assertThat(cancelled.status()).isEqualTo(RateAlertStatus.CANCELLED);
        assertThat(cancelled.cancelledAt()).isNotNull();
    }

    @Test
    void cancel_alreadyCancelledAlert_throwsInactive() {
        User user = createUser(RoleCode.USER);
        RateAlertResponse created = rateAlertService.create(
                new CreateRateAlertRequest(null, null, new BigDecimal("83.50"), null, null), user.getId());
        rateAlertService.cancel(created.id(), user.getId());

        assertThatThrownBy(() -> rateAlertService.cancel(created.id(), user.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode().name()).isEqualTo("RATE_ALERT_INACTIVE"));
    }

    // ---- 17/18 : doublons et isolation multi-utilisateurs ----

    @Test
    void create_identicalAlertsTwice_bothPersistedIndependently() {
        User user = createUser(RoleCode.USER);
        RateAlertResponse a = rateAlertService.create(
                new CreateRateAlertRequest(null, null, new BigDecimal("83.50"), null, null), user.getId());
        RateAlertResponse b = rateAlertService.create(
                new CreateRateAlertRequest(null, null, new BigDecimal("83.50"), null, null), user.getId());

        assertThat(a.id()).isNotEqualTo(b.id());
        assertThat(rateAlertRepository.findById(a.id())).isPresent();
        assertThat(rateAlertRepository.findById(b.id())).isPresent();
    }

    @Test
    void listMine_isolatesAlertsPerUser_sameThreshold() {
        User userA = createUser(RoleCode.USER);
        User userB = createUser(RoleCode.USER);
        rateAlertService.create(new CreateRateAlertRequest(null, null, new BigDecimal("83.50"), null, null), userA.getId());
        rateAlertService.create(new CreateRateAlertRequest(null, null, new BigDecimal("83.50"), null, null), userB.getId());

        var pageA = rateAlertService.listMine(userA.getId(), null, PageRequest.of(0, 20));
        var pageB = rateAlertService.listMine(userB.getId(), null, PageRequest.of(0, 20));

        assertThat(pageA.content()).hasSize(1);
        assertThat(pageB.content()).hasSize(1);
        assertThat(pageA.content().get(0).id()).isNotEqualTo(pageB.content().get(0).id());
    }

    @Test
    void listMine_filtersByStatus() {
        User user = createUser(RoleCode.USER);
        RateAlertResponse active = rateAlertService.create(
                new CreateRateAlertRequest(null, null, new BigDecimal("83.50"), null, null), user.getId());
        RateAlertResponse toCancel = rateAlertService.create(
                new CreateRateAlertRequest(null, null, new BigDecimal("80.00"), null, null), user.getId());
        rateAlertService.cancel(toCancel.id(), user.getId());

        var activeOnly = rateAlertService.listMine(user.getId(), RateAlertStatus.ACTIVE, PageRequest.of(0, 20));

        assertThat(activeOnly.content()).extracting(RateAlertResponse::id).containsExactly(active.id());
    }

    // ---- 9/10/11/12/13/14/15 : scheduler ----

    @Test
    void processOne_conditionNotSatisfied_staysActive() {
        resetMarginToZero();
        String admin = adminToken();
        publishCostRate(admin, "85.000000");
        User user = createUser(RoleCode.USER);
        RateAlertResponse alert = rateAlertService.create(
                new CreateRateAlertRequest(null, null, new BigDecimal("80.00"), null, null), user.getId());

        rateAlertService.processOne(alert.id());

        assertThat(rateAlertRepository.findById(alert.id()).orElseThrow().getStatus()).isEqualTo(RateAlertStatus.ACTIVE);
    }

    @Test
    void processOne_conditionSatisfied_triggersAndCreatesNotification() {
        resetMarginToZero();
        String admin = adminToken();
        User user = createUser(RoleCode.USER);
        RateAlertResponse alert = rateAlertService.create(
                new CreateRateAlertRequest(null, null, new BigDecimal("83.50"), null, null), user.getId());
        publishCostRate(admin, "83.000000");

        rateAlertService.processOne(alert.id());

        RateAlert reloaded = rateAlertRepository.findById(alert.id()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(RateAlertStatus.TRIGGERED);
        assertThat(reloaded.getTriggeredAt()).isNotNull();

        List<Notification> notifications = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(0, 20)).getContent();
        assertThat(notifications).extracting(Notification::getType).contains(NotificationType.RATE_ALERT_TRIGGERED);
    }

    @Test
    void processOne_equalityCase_triggers() {
        resetMarginToZero();
        String admin = adminToken();
        User user = createUser(RoleCode.USER);
        RateAlertResponse alert = rateAlertService.create(
                new CreateRateAlertRequest(null, null, new BigDecimal("83.500000"), null, null), user.getId());
        publishCostRate(admin, "83.500000");

        rateAlertService.processOne(alert.id());

        assertThat(rateAlertRepository.findById(alert.id()).orElseThrow().getStatus()).isEqualTo(RateAlertStatus.TRIGGERED);
    }

    @Test
    void processOne_pastExpiration_expiresWithoutNotification_evenIfRateWouldSatisfy() {
        resetMarginToZero();
        String admin = adminToken();
        User user = createUser(RoleCode.USER);
        publishCostRate(admin, "80.000000");

        // Construction directe (hors service.create(), qui rejetterait une echeance deja passee a
        // la creation -- section 10) : simule une alerte creee valide dont l'echeance vient de
        // passer, sans dependre d'un sleep reel dans le test.
        RateAlert alert = rateAlertRepository.save(new RateAlert(user.getId(), "XOF/CNY",
                PreferredRateDirection.XOF_TO_CNY, new BigDecimal("83.50"), RateComparison.LESS_THAN_OR_EQUAL,
                Instant.now().minusSeconds(10), Instant.now().minusSeconds(1)));

        rateAlertService.processOne(alert.getId());

        RateAlert reloaded = rateAlertRepository.findById(alert.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(RateAlertStatus.EXPIRED);
        List<Notification> notifications = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(0, 20)).getContent();
        assertThat(notifications).extracting(Notification::getType).doesNotContain(NotificationType.RATE_ALERT_TRIGGERED);
    }

    @Test
    void processOne_noPublicRateSnapshotForPair_skipsEvaluation_staysActive() {
        // Paire volontairement hors whitelist (jamais atteignable via create(), qui la rejetterait) :
        // insertion directe au niveau repository pour prouver le comportement de processOne seul,
        // immunise contre le volume de snapshots XOF/CNY deja accumule par le reste de la suite.
        User user = createUser(RoleCode.USER);
        // currency_pair est VARCHAR(10) : suffixe court, garanti sans aucun snapshot public existant.
        String uncoveredPair = "Z/" + UUID.randomUUID().toString().substring(0, 6);
        RateAlert alert = rateAlertRepository.save(new RateAlert(user.getId(), uncoveredPair,
                PreferredRateDirection.XOF_TO_CNY, new BigDecimal("83.50"), RateComparison.LESS_THAN_OR_EQUAL,
                Instant.now(), null));

        rateAlertService.processOne(alert.getId());

        assertThat(rateAlertRepository.findById(alert.getId()).orElseThrow().getStatus()).isEqualTo(RateAlertStatus.ACTIVE);
    }

    @Test
    void processOne_secondPassAfterTrigger_isNoOp_noSecondNotification() {
        resetMarginToZero();
        String admin = adminToken();
        User user = createUser(RoleCode.USER);
        RateAlertResponse alert = rateAlertService.create(
                new CreateRateAlertRequest(null, null, new BigDecimal("83.50"), null, null), user.getId());
        publishCostRate(admin, "83.000000");

        rateAlertService.processOne(alert.id());
        rateAlertService.processOne(alert.id());

        List<Notification> notifications = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(0, 20)).getContent();
        long triggeredCount = notifications.stream().filter(n -> n.getType() == NotificationType.RATE_ALERT_TRIGGERED).count();
        assertThat(triggeredCount).isEqualTo(1);
    }

    // ---- 16 : deja couvert par processOne_noPublicRateSnapshotForPair_skipsEvaluation_staysActive ----

    // ---- 34 : concurrence obligatoire ----

    @Test
    void twoConcurrentSchedulerPasses_triggerOnlyOnce_oneNotification() throws Exception {
        resetMarginToZero();
        String admin = adminToken();
        User user = createUser(RoleCode.USER);
        RateAlertResponse alert = rateAlertService.create(
                new CreateRateAlertRequest(null, null, new BigDecimal("83.50"), null, null), user.getId());
        publishCostRate(admin, "83.000000");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> f1 = executor.submit(() -> rateAlertService.processOne(alert.id()));
            Future<?> f2 = executor.submit(() -> rateAlertService.processOne(alert.id()));
            f1.get(30, TimeUnit.SECONDS);
            f2.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdown();
        }

        assertThat(rateAlertRepository.findById(alert.id()).orElseThrow().getStatus()).isEqualTo(RateAlertStatus.TRIGGERED);
        List<Notification> notifications = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(0, 20)).getContent();
        long triggeredCount = notifications.stream().filter(n -> n.getType() == NotificationType.RATE_ALERT_TRIGGERED).count();
        assertThat(triggeredCount).isEqualTo(1);
    }

    // ---- Correctif : publication d'un taux manuel seul (sans chemin de cout) doit deja notifier ----

    @Test
    void processOne_afterManualRatePublishOnly_triggersAndNotifies() {
        // Regression : avant le correctif de RateAdminService#publishManualRate, seul
        // CostRateAdminService#publish faisait progresser public_rate_snapshots -- un
        // administrateur publiant un nouveau taux manuel (POST /api/admin/rates, le mecanisme de
        // "changement de taux" le plus direct) ne declenchait donc jamais les alertes des clients
        // qui l'attendaient. publishManualRateOnly() n'appelle JAMAIS le chemin de cout distinct,
        // contrairement a publishRate() utilise par le reste de cette suite.
        resetMarginToZero();
        String admin = adminToken();
        User user = createUser(RoleCode.USER);
        RateAlertResponse alert = rateAlertService.create(
                new CreateRateAlertRequest(null, null, new BigDecimal("83.50"), null, null), user.getId());

        publishManualRateOnly(admin, "83.000000");
        rateAlertService.processOne(alert.id());

        RateAlert reloaded = rateAlertRepository.findById(alert.id()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(RateAlertStatus.TRIGGERED);
        List<Notification> notifications = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(0, 20)).getContent();
        assertThat(notifications).extracting(Notification::getType).contains(NotificationType.RATE_ALERT_TRIGGERED);
    }

    // ---- 37 : persistance des transitions ("redemarrage") ----

    @Test
    void triggeredCancelledExpired_persistAcrossFreshReads_neverReturnedAsActiveAgain() {
        resetMarginToZero();
        String admin = adminToken();
        User user = createUser(RoleCode.USER);

        RateAlertResponse triggeredAlert = rateAlertService.create(
                new CreateRateAlertRequest(null, null, new BigDecimal("83.50"), null, null), user.getId());
        publishCostRate(admin, "83.000000");
        rateAlertService.processOne(triggeredAlert.id());

        RateAlertResponse cancelledAlert = rateAlertService.create(
                new CreateRateAlertRequest(null, null, new BigDecimal("70.00"), null, null), user.getId());
        rateAlertService.cancel(cancelledAlert.id(), user.getId());

        // Une lecture fraiche depuis le repository (nouvelle requete, pas l'instance en memoire du
        // scheduler) doit refleter l'etat final -- la base est la seule source de verite, jamais
        // une variable en memoire du processus (section 37/40).
        assertThat(rateAlertRepository.findById(triggeredAlert.id()).orElseThrow().getStatus())
                .isEqualTo(RateAlertStatus.TRIGGERED);
        assertThat(rateAlertRepository.findById(cancelledAlert.id()).orElseThrow().getStatus())
                .isEqualTo(RateAlertStatus.CANCELLED);

        List<UUID> stillActive = rateAlertService.activeAlertIds();
        assertThat(stillActive).doesNotContain(triggeredAlert.id(), cancelledAlert.id());
    }
}
