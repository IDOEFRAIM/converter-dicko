package com.converter.business.profile.web;

import com.converter.audit.domain.AuditAction;
import com.converter.audit.repository.AuditLogRepository;
import com.converter.business.profile.domain.BusinessType;
import com.converter.business.profile.dto.BusinessProfileResponse;
import com.converter.business.profile.dto.UpsertBusinessProfileRequest;
import com.converter.business.profile.repository.BusinessProfileRepository;
import com.converter.common.api.ApiResponse;
import com.converter.common.api.ErrorResponse;
import com.converter.support.AbstractRateQuoteIT;
import com.converter.user.domain.RoleCode;
import com.converter.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code /api/v1/business-profile} : creation/mise a jour idempotente (PUT), ownership implicite
 * (aucun {@code userId} dans le corps), validation, audit, et la garantie de concurrence de la
 * section 40 (contrainte {@code UNIQUE(user_id)}).
 */
class BusinessProfileHttpIT extends AbstractRateQuoteIT {

    @Autowired
    private BusinessProfileRepository businessProfileRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private ResponseEntity<ApiResponse<BusinessProfileResponse>> putProfile(String token,
                                                                            UpsertBusinessProfileRequest request) {
        return restTemplate.exchange("/api/v1/business-profile", HttpMethod.PUT,
                new HttpEntity<>(request, headers(token)),
                new ParameterizedTypeReference<ApiResponse<BusinessProfileResponse>>() {
                });
    }

    private ResponseEntity<ApiResponse<BusinessProfileResponse>> getProfile(String token) {
        return restTemplate.exchange("/api/v1/business-profile", HttpMethod.GET,
                new HttpEntity<>(headers(token)),
                new ParameterizedTypeReference<ApiResponse<BusinessProfileResponse>>() {
                });
    }

    private UpsertBusinessProfileRequest sampleRequest() {
        return new UpsertBusinessProfileRequest("Zhang Trading SARL", BusinessType.IMPORTER, "RCCM-BF-001",
                "Burkina Faso", "Ouagadougou", "Secteur 15, Avenue Kwame Nkrumah");
    }

    @Test
    void get_noProfile_returns404() {
        String user = tokenFor(createUser(RoleCode.USER));

        ResponseEntity<ErrorResponse> response = restTemplate.exchange("/api/v1/business-profile", HttpMethod.GET,
                new HttpEntity<>(headers(user)), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("BUSINESS_PROFILE_NOT_FOUND");
    }

    @Test
    void get_anonymous_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/business-profile", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void put_noExistingProfile_creates201() {
        String user = tokenFor(createUser(RoleCode.USER));

        ResponseEntity<ApiResponse<BusinessProfileResponse>> response = putProfile(user, sampleRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        BusinessProfileResponse body = response.getBody().data();
        assertThat(body.businessName()).isEqualTo("Zhang Trading SARL");
        assertThat(body.businessType()).isEqualTo(BusinessType.IMPORTER);
        assertThat(body.registrationNumber()).isEqualTo("RCCM-BF-001");
        assertThat(body.country()).isEqualTo("Burkina Faso");
    }

    @Test
    void put_existingProfile_updates200_neverCreatesASecondRow() {
        User user = createUser(RoleCode.USER);
        String token = tokenFor(user);
        putProfile(token, sampleRequest());

        ResponseEntity<ApiResponse<BusinessProfileResponse>> response = putProfile(token,
                new UpsertBusinessProfileRequest("Zhang Trading Renamed", BusinessType.MERCHANT, null,
                        "Burkina Faso", "Bobo-Dioulasso", null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().businessName()).isEqualTo("Zhang Trading Renamed");
        assertThat(response.getBody().data().businessType()).isEqualTo(BusinessType.MERCHANT);
        assertThat(response.getBody().data().registrationNumber()).isNull();

        assertThat(businessProfileRepository.findByUserId(user.getId())).isPresent();
    }

    @Test
    void get_afterCreation_returnsSameData() {
        String user = tokenFor(createUser(RoleCode.USER));
        putProfile(user, sampleRequest());

        ResponseEntity<ApiResponse<BusinessProfileResponse>> response = getProfile(user);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().businessName()).isEqualTo("Zhang Trading SARL");
    }

    @Test
    void profiles_areIsolatedPerUser() {
        String userA = tokenFor(createUser(RoleCode.USER));
        String userB = tokenFor(createUser(RoleCode.USER));
        putProfile(userA, sampleRequest());
        putProfile(userB, new UpsertBusinessProfileRequest("Autre Entreprise", BusinessType.SERVICES, null,
                "Cote d'Ivoire", "Abidjan", null));

        BusinessProfileResponse profileA = getProfile(userA).getBody().data();
        BusinessProfileResponse profileB = getProfile(userB).getBody().data();

        assertThat(profileA.businessName()).isEqualTo("Zhang Trading SARL");
        assertThat(profileB.businessName()).isEqualTo("Autre Entreprise");
        assertThat(profileA.id()).isNotEqualTo(profileB.id());
    }

    @Test
    void put_blankBusinessName_returns400() {
        String user = tokenFor(createUser(RoleCode.USER));

        ResponseEntity<ErrorResponse> response = restTemplate.exchange("/api/v1/business-profile", HttpMethod.PUT,
                new HttpEntity<>(new UpsertBusinessProfileRequest("", BusinessType.IMPORTER, null,
                        "Burkina Faso", null, null), headers(user)),
                ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void put_missingBusinessType_returns400() {
        String user = tokenFor(createUser(RoleCode.USER));

        ResponseEntity<ErrorResponse> response = restTemplate.exchange("/api/v1/business-profile", HttpMethod.PUT,
                new HttpEntity<>(new UpsertBusinessProfileRequest("Nom", null, null, "Burkina Faso", null, null),
                        headers(user)),
                ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void put_blankCountry_returns400() {
        String user = tokenFor(createUser(RoleCode.USER));

        ResponseEntity<ErrorResponse> response = restTemplate.exchange("/api/v1/business-profile", HttpMethod.PUT,
                new HttpEntity<>(new UpsertBusinessProfileRequest("Nom", BusinessType.OTHER, null, "", null, null),
                        headers(user)),
                ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void put_create_recordsAuditEvent() {
        User user = createUser(RoleCode.USER);
        BusinessProfileResponse created = putProfile(tokenFor(user), sampleRequest()).getBody().data();

        // Lecture par (entityType, entityId) concrets via le finder derive dedie -- jamais
        // AuditLogRepository#search, dont le patron (:p IS NULL OR ...) laisse le parametre de
        // borne temporelle sans type inferable par PostgreSQL (voir Javadoc du finder + rapport
        // de cloture Phase 8).
        var entries = auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                "BusinessProfile", created.id().toString());

        assertThat(entries).extracting(com.converter.audit.domain.AuditLog::getAction)
                .contains(AuditAction.BUSINESS_PROFILE_CREATED);
        assertThat(entries).allSatisfy(entry -> assertThat(entry.getActorId()).isEqualTo(user.getId()));
    }

    @Test
    void put_update_recordsAuditEvent() {
        User user = createUser(RoleCode.USER);
        String token = tokenFor(user);
        BusinessProfileResponse created = putProfile(token, sampleRequest()).getBody().data();
        putProfile(token, new UpsertBusinessProfileRequest("Renomme", BusinessType.OTHER, null, "Burkina Faso",
                null, null));

        var entries = auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                "BusinessProfile", created.id().toString());

        assertThat(entries).extracting(com.converter.audit.domain.AuditLog::getAction)
                .contains(AuditAction.BUSINESS_PROFILE_CREATED, AuditAction.BUSINESS_PROFILE_UPDATED);
    }

    /** Section 40 : deux creations concurrentes pour le meme utilisateur -> un seul BusinessProfile persiste. */
    @Test
    void twoConcurrentCreations_forSameUser_resultInExactlyOneProfile() throws Exception {
        User user = createUser(RoleCode.USER);
        String token = tokenFor(user);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ResponseEntity<ApiResponse<BusinessProfileResponse>>> f1 =
                    executor.submit(() -> putProfile(token, sampleRequest()));
            Future<ResponseEntity<ApiResponse<BusinessProfileResponse>>> f2 =
                    executor.submit(() -> putProfile(token, sampleRequest()));
            ResponseEntity<ApiResponse<BusinessProfileResponse>> r1 = f1.get(30, TimeUnit.SECONDS);
            ResponseEntity<ApiResponse<BusinessProfileResponse>> r2 = f2.get(30, TimeUnit.SECONDS);

            // Chaque appel individuel reussit (201 ou 200 selon l'ordre reel d'execution) ou
            // echoue proprement (409, si les deux INSERT se chevauchent exactement) -- jamais
            // une erreur 5xx.
            assertThat(r1.getStatusCode().is2xxSuccessful() || r1.getStatusCode() == HttpStatus.CONFLICT).isTrue();
            assertThat(r2.getStatusCode().is2xxSuccessful() || r2.getStatusCode() == HttpStatus.CONFLICT).isTrue();
        } finally {
            executor.shutdown();
        }

        // Le seul invariant qui compte reellement : un seul profil en base pour cet utilisateur.
        assertThat(businessProfileRepository.findByUserId(user.getId())).isPresent();
        long total = businessProfileRepository.findAll().stream()
                .filter(p -> p.getUserId().equals(user.getId())).count();
        assertThat(total).isEqualTo(1);
    }
}
