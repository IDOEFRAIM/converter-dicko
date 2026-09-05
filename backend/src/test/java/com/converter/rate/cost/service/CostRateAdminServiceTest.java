package com.converter.rate.cost.service;

import com.converter.audit.domain.AuditAction;
import com.converter.audit.service.AuditService;
import com.converter.common.exception.BusinessException;
import com.converter.rate.cost.CostRateCalculator;
import com.converter.rate.cost.domain.DailyCostRateConfiguration;
import com.converter.rate.cost.dto.CostRateConfigurationResponse;
import com.converter.rate.cost.dto.PublishCostRateConfigurationRequest;
import com.converter.rate.cost.repository.DailyCostRateConfigurationRepository;
import com.converter.rate.publicrate.service.PublicRateSnapshotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link CostRateAdminService}, avec le repository et
 * {@link AuditService} entierement simules : verifie que le calcul du
 * breakEvenRate est delegue a {@link CostRateCalculator} (jamais
 * recalcule autrement) et que la publication est bien auditee.
 */
@ExtendWith(MockitoExtension.class)
class CostRateAdminServiceTest {

    @Mock
    private DailyCostRateConfigurationRepository repository;

    @Mock
    private AuditService auditService;

    @Mock
    private PublicRateSnapshotService publicRateSnapshotService;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-02T12:00:00Z"), ZoneOffset.UTC);
    private final UUID actorId = UUID.randomUUID();

    private CostRateAdminService service;

    @BeforeEach
    void setUp() {
        service = new CostRateAdminService(repository, new CostRateCalculator(), publicRateSnapshotService,
                auditService, clock);
    }

    @Test
    void publish_computesBreakEvenRateAndPersistsIt() {
        when(repository.save(any(DailyCostRateConfiguration.class)))
                .thenAnswer(invocation -> {
                    DailyCostRateConfiguration configuration = invocation.getArgument(0);
                    configuration.setId(UUID.randomUUID());
                    return configuration;
                });

        PublishCostRateConfigurationRequest request = new PublishCostRateConfigurationRequest(
                LocalDate.parse("2026-09-02"), new BigDecimal("583"), new BigDecimal("6.70"),
                new BigDecimal("0.01"), new BigDecimal("1.50"), new BigDecimal("1000000"), "note test");

        CostRateConfigurationResponse response = service.publish(request, actorId);

        assertThat(response.breakEvenRate()).isEqualByComparingTo("87.971572");
        assertThat(response.referenceAmountXof()).isEqualByComparingTo("1000000");
        assertThat(response.businessDate()).isEqualTo(LocalDate.parse("2026-09-02"));

        ArgumentCaptor<DailyCostRateConfiguration> captor = ArgumentCaptor.forClass(DailyCostRateConfiguration.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getBreakEvenRate()).isEqualByComparingTo("87.971572");
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(actorId);

        verify(auditService).record(eq(actorId), isNull(), eq(AuditAction.COST_RATE_CONFIGURATION_PUBLISHED),
                anyString(), anyString(), anyString());

        // La publication d'un snapshot public est additive : verifie qu'elle recoit exactement
        // le breakEvenRate venant d'etre calcule, jamais la configuration elle-meme.
        verify(publicRateSnapshotService).record(eq(new BigDecimal("87.971572")), anyString(), eq(clock.instant()));
    }

    @Test
    void publish_withInvalidParameters_neverPersistsAnything() {
        // usdNet <= 0 : les frais depassent le montant converti.
        PublishCostRateConfigurationRequest request = new PublishCostRateConfigurationRequest(
                LocalDate.parse("2026-09-02"), new BigDecimal("583"), new BigDecimal("6.70"),
                BigDecimal.ZERO, new BigDecimal("100000"), new BigDecimal("100"), null);

        assertThatThrownBy(() -> service.publish(request, actorId)).isInstanceOf(BusinessException.class);

        verify(repository, never()).save(any());
        verify(auditService, never()).record(any(), any(), any(), anyString(), anyString(), anyString());
        verify(publicRateSnapshotService, never()).record(any(), any(), any());
    }

    /**
     * Section 24 : si la persistance du snapshot public echoue, la publication ne doit jamais
     * etre consideree comme reussie — l'exception doit se propager (pas de try/catch qui
     * l'absorberait), ce qui declenche le rollback standard de {@code @Transactional} sur toute
     * l'operation (la configuration de cout deja sauvegardee y compris), sans rien de special a
     * coder ici.
     */
    @Test
    void publish_whenPublicSnapshotPersistenceFails_propagatesTheExceptionRatherThanSwallowingIt() {
        when(repository.save(any(DailyCostRateConfiguration.class)))
                .thenAnswer(invocation -> {
                    DailyCostRateConfiguration configuration = invocation.getArgument(0);
                    configuration.setId(UUID.randomUUID());
                    return configuration;
                });
        org.mockito.Mockito.doThrow(new RuntimeException("DB down"))
                .when(publicRateSnapshotService).record(any(), anyString(), any());

        PublishCostRateConfigurationRequest request = new PublishCostRateConfigurationRequest(
                LocalDate.parse("2026-09-02"), new BigDecimal("583"), new BigDecimal("6.70"),
                new BigDecimal("0.01"), new BigDecimal("1.50"), new BigDecimal("1000000"), "note test");

        assertThatThrownBy(() -> service.publish(request, actorId)).isInstanceOf(RuntimeException.class);

        // L'audit n'a jamais lieu si le snapshot echoue avant lui : l'exception interrompt
        // publish() au point d'echec, exactement comme n'importe quelle autre exception non
        // rattrapee dans ce service.
        verify(auditService, never()).record(any(), any(), any(), anyString(), anyString(), anyString());
    }

    @Test
    void current_withNoConfigurationPublished_throwsBusinessException() {
        when(repository.findFirstByOrderByCreatedAtDesc()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.current()).isInstanceOf(BusinessException.class);
    }
}
