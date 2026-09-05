package com.converter.rate.alert.service;

import com.converter.audit.domain.AuditAction;
import com.converter.audit.service.AuditService;
import com.converter.notification.domain.NotificationType;
import com.converter.notification.service.NotificationService;
import com.converter.preferredrate.domain.PreferredRateDirection;
import com.converter.rate.alert.domain.RateAlert;
import com.converter.rate.alert.domain.RateAlertStatus;
import com.converter.rate.alert.domain.RateComparison;
import com.converter.rate.alert.repository.RateAlertRepository;
import com.converter.rate.publicrate.service.PublicRateSnapshotService;
import com.converter.security.OwnershipService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@code RateAlertService#processOne}, avec repository/notification/audit
 * entierement simules — meme style que {@code CostRateAdminServiceTest}. L'entite {@link RateAlert}
 * elle-meme est reelle (pas simulee) : ses transitions ({@code trigger}/{@code expire}) s'executent
 * pour de vrai, seule la persistance est simulee.
 */
@ExtendWith(MockitoExtension.class)
class RateAlertServiceTest {

    @Mock
    private RateAlertRepository repository;

    @Mock
    private PublicRateSnapshotService publicRateSnapshotService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AuditService auditService;

    @Mock
    private OwnershipService ownershipService;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-03T12:00:00Z"), ZoneOffset.UTC);
    private final UUID userId = UUID.randomUUID();

    private RateAlertService service;

    @BeforeEach
    void setUp() {
        service = new RateAlertService(repository, publicRateSnapshotService, notificationService, auditService,
                ownershipService, clock);
    }

    private RateAlert activeAlert(BigDecimal targetRate, RateComparison comparison, Instant expiresAt) {
        RateAlert alert = new RateAlert(userId, "XOF/CNY", PreferredRateDirection.XOF_TO_CNY, targetRate, comparison,
                clock.instant().minusSeconds(60), expiresAt);
        // Jamais persistee ici (repository entierement simule) : l'id doit etre affecte a la main,
        // comme CostRateAdminServiceTest le fait via configuration.setId(...) dans son stub de save().
        alert.setId(UUID.randomUUID());
        return alert;
    }

    @Test
    void processOne_conditionNotSatisfied_staysActive_noNotification() {
        RateAlert alert = activeAlert(new BigDecimal("83.50"), RateComparison.LESS_THAN_OR_EQUAL, null);
        when(repository.findByIdForUpdate(alert.getId())).thenReturn(Optional.of(alert));
        when(publicRateSnapshotService.latestCustomerRate("XOF/CNY")).thenReturn(Optional.of(new BigDecimal("84.20")));

        service.processOne(alert.getId());

        assertThat(alert.getStatus()).isEqualTo(RateAlertStatus.ACTIVE);
        verify(notificationService, never()).create(any(), any(), anyString(), anyString());
        verify(auditService, never()).recordSystem(any(), anyString(), anyString(), any());
    }

    @Test
    void processOne_conditionSatisfied_triggersAndNotifiesExactlyOnce() {
        RateAlert alert = activeAlert(new BigDecimal("83.50"), RateComparison.LESS_THAN_OR_EQUAL, null);
        when(repository.findByIdForUpdate(alert.getId())).thenReturn(Optional.of(alert));
        when(publicRateSnapshotService.latestCustomerRate("XOF/CNY")).thenReturn(Optional.of(new BigDecimal("83.20")));

        service.processOne(alert.getId());

        assertThat(alert.getStatus()).isEqualTo(RateAlertStatus.TRIGGERED);
        assertThat(alert.getTriggeredAt()).isEqualTo(clock.instant());
        verify(auditService).recordSystem(eq(AuditAction.RATE_ALERT_TRIGGERED), eq("RateAlert"),
                eq(alert.getId().toString()), isNull());
        verify(notificationService).create(eq(userId), eq(NotificationType.RATE_ALERT_TRIGGERED), anyString(), anyString());
    }

    @Test
    void processOne_equalityCase_isSatisfied() {
        RateAlert alert = activeAlert(new BigDecimal("83.50"), RateComparison.LESS_THAN_OR_EQUAL, null);
        when(repository.findByIdForUpdate(alert.getId())).thenReturn(Optional.of(alert));
        when(publicRateSnapshotService.latestCustomerRate("XOF/CNY")).thenReturn(Optional.of(new BigDecimal("83.50")));

        service.processOne(alert.getId());

        assertThat(alert.getStatus()).isEqualTo(RateAlertStatus.TRIGGERED);
    }

    @Test
    void processOne_pastDeadline_expiresWithoutEvaluatingRate_noNotification() {
        RateAlert alert = activeAlert(new BigDecimal("83.50"), RateComparison.LESS_THAN_OR_EQUAL,
                clock.instant().minusSeconds(1));
        when(repository.findByIdForUpdate(alert.getId())).thenReturn(Optional.of(alert));

        service.processOne(alert.getId());

        assertThat(alert.getStatus()).isEqualTo(RateAlertStatus.EXPIRED);
        // L'expiration l'emporte : le taux courant n'est jamais consulte, meme si la cible serait satisfaite.
        verify(publicRateSnapshotService, never()).latestCustomerRate(anyString());
        verify(notificationService, never()).create(any(), any(), anyString(), anyString());
    }

    @Test
    void processOne_expiresExactlyAtDeadline_isTreatedAsExpired() {
        // isPastDeadline : !now.isBefore(expiresAt) -- l'instant exact de l'echeance compte deja comme depasse.
        RateAlert alert = activeAlert(new BigDecimal("83.50"), RateComparison.LESS_THAN_OR_EQUAL, clock.instant());
        when(repository.findByIdForUpdate(alert.getId())).thenReturn(Optional.of(alert));

        service.processOne(alert.getId());

        assertThat(alert.getStatus()).isEqualTo(RateAlertStatus.EXPIRED);
    }

    @Test
    void processOne_noPublicRateAvailable_skipsEvaluation_staysActive() {
        RateAlert alert = activeAlert(new BigDecimal("83.50"), RateComparison.LESS_THAN_OR_EQUAL, null);
        when(repository.findByIdForUpdate(alert.getId())).thenReturn(Optional.of(alert));
        when(publicRateSnapshotService.latestCustomerRate("XOF/CNY")).thenReturn(Optional.empty());

        service.processOne(alert.getId());

        assertThat(alert.getStatus()).isEqualTo(RateAlertStatus.ACTIVE);
        verify(notificationService, never()).create(any(), any(), anyString(), anyString());
    }

    @Test
    void processOne_alertAlreadyNotActive_isNoOp_doubleCheckGuard() {
        RateAlert alert = activeAlert(new BigDecimal("83.50"), RateComparison.LESS_THAN_OR_EQUAL, null);
        alert.cancel(clock.instant());
        when(repository.findByIdForUpdate(alert.getId())).thenReturn(Optional.of(alert));

        service.processOne(alert.getId());

        assertThat(alert.getStatus()).isEqualTo(RateAlertStatus.CANCELLED);
        verify(publicRateSnapshotService, never()).latestCustomerRate(anyString());
        verify(notificationService, never()).create(any(), any(), anyString(), anyString());
    }

    @Test
    void processOne_alertNoLongerFound_isNoOp() {
        UUID missingId = UUID.randomUUID();
        when(repository.findByIdForUpdate(missingId)).thenReturn(Optional.empty());

        service.processOne(missingId);

        verify(publicRateSnapshotService, never()).latestCustomerRate(anyString());
    }

    /**
     * Section 35 : simule l'echec de {@code NotificationService.create} (le contrat reel absorbe
     * deja toute exception et renvoie {@code null}, voir {@code NotificationService} — jamais une
     * exception propagee vers l'appelant). Verifie que le declenchement metier reste acquis :
     * l'alerte ne revient jamais a {@code ACTIVE}.
     */
    @Test
    void processOne_notificationCreationReturnsNull_triggerStaysAcquired() {
        RateAlert alert = activeAlert(new BigDecimal("83.50"), RateComparison.LESS_THAN_OR_EQUAL, null);
        when(repository.findByIdForUpdate(alert.getId())).thenReturn(Optional.of(alert));
        when(publicRateSnapshotService.latestCustomerRate("XOF/CNY")).thenReturn(Optional.of(new BigDecimal("83.00")));
        when(notificationService.create(any(), any(), anyString(), anyString())).thenReturn(null);

        service.processOne(alert.getId());

        assertThat(alert.getStatus()).isEqualTo(RateAlertStatus.TRIGGERED);
    }
}
