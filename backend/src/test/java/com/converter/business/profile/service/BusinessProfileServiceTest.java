package com.converter.business.profile.service;

import com.converter.audit.domain.AuditAction;
import com.converter.audit.service.AuditService;
import com.converter.business.profile.domain.BusinessProfile;
import com.converter.business.profile.domain.BusinessType;
import com.converter.business.profile.dto.BusinessProfileResponse;
import com.converter.business.profile.dto.UpsertBusinessProfileRequest;
import com.converter.business.profile.repository.BusinessProfileRepository;
import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@code BusinessProfileService} — repository/audit simules, meme style que
 * {@code RateAlertServiceTest}. Couvre en particulier la regle "PUT idempotent" (creation si
 * absent, mise a jour sinon) et {@code isBusinessUser}, la source unique de verite de la
 * distinction PERSONAL/BUSINESS.
 */
@ExtendWith(MockitoExtension.class)
class BusinessProfileServiceTest {

    @Mock
    private BusinessProfileRepository repository;

    @Mock
    private AuditService auditService;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-03T12:00:00Z"), ZoneOffset.UTC);
    private final UUID userId = UUID.randomUUID();

    private BusinessProfileService service;

    @BeforeEach
    void setUp() {
        service = new BusinessProfileService(repository, auditService, clock);
    }

    @Test
    void isBusinessUser_noProfile_isFalse() {
        when(repository.existsByUserId(userId)).thenReturn(false);

        assertThat(service.isBusinessUser(userId)).isFalse();
    }

    @Test
    void isBusinessUser_profileExists_isTrue() {
        when(repository.existsByUserId(userId)).thenReturn(true);

        assertThat(service.isBusinessUser(userId)).isTrue();
    }

    @Test
    void get_noProfile_throwsNotFound() {
        when(repository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(userId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode())
                        .isEqualTo(ErrorCode.BUSINESS_PROFILE_NOT_FOUND));
    }

    @Test
    void upsert_noExistingProfile_createsAndAudits() {
        when(repository.findByUserId(userId)).thenReturn(Optional.empty());
        when(repository.save(org.mockito.ArgumentMatchers.any(BusinessProfile.class))).thenAnswer(invocation -> {
            BusinessProfile profile = invocation.getArgument(0);
            profile.setId(UUID.randomUUID());
            return profile;
        });

        UpsertBusinessProfileRequest request = new UpsertBusinessProfileRequest("Zhang Trading", BusinessType.IMPORTER,
                null, "Burkina Faso", "Ouagadougou", "Secteur 15");

        BusinessProfileService.UpsertResult result = service.upsert(request, userId);

        assertThat(result.created()).isTrue();
        BusinessProfileResponse response = result.response();
        assertThat(response.businessName()).isEqualTo("Zhang Trading");
        assertThat(response.businessType()).isEqualTo(BusinessType.IMPORTER);
        assertThat(response.registrationNumber()).isNull();
        assertThat(response.country()).isEqualTo("Burkina Faso");

        verify(auditService).record(eq(userId), isNull(), eq(AuditAction.BUSINESS_PROFILE_CREATED),
                eq("BusinessProfile"), anyString(), isNull());
    }

    @Test
    void upsert_existingProfile_updatesInPlace_neverCreatesASecondRow() {
        BusinessProfile existing = new BusinessProfile(userId, "Old Name", BusinessType.OTHER, "RC-001",
                "Burkina Faso", "Bobo-Dioulasso", null, Instant.parse("2026-08-01T00:00:00Z"));
        existing.setId(UUID.randomUUID());
        when(repository.findByUserId(userId)).thenReturn(Optional.of(existing));

        UpsertBusinessProfileRequest request = new UpsertBusinessProfileRequest("New Name", BusinessType.MERCHANT,
                "RC-002", "Burkina Faso", "Ouagadougou", "Nouvelle adresse");

        BusinessProfileService.UpsertResult result = service.upsert(request, userId);

        assertThat(result.created()).isFalse();
        assertThat(result.response().businessName()).isEqualTo("New Name");
        assertThat(result.response().businessType()).isEqualTo(BusinessType.MERCHANT);
        assertThat(result.response().registrationNumber()).isEqualTo("RC-002");
        assertThat(result.response().city()).isEqualTo("Ouagadougou");

        verify(repository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
        ArgumentCaptor<AuditAction> actionCaptor = ArgumentCaptor.forClass(AuditAction.class);
        verify(auditService).record(eq(userId), isNull(), actionCaptor.capture(), eq("BusinessProfile"),
                anyString(), isNull());
        assertThat(actionCaptor.getValue()).isEqualTo(AuditAction.BUSINESS_PROFILE_UPDATED);
    }
}
