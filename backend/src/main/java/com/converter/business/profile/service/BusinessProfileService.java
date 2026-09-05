package com.converter.business.profile.service;

import com.converter.audit.domain.AuditAction;
import com.converter.audit.service.AuditService;
import com.converter.business.profile.domain.BusinessProfile;
import com.converter.business.profile.dto.BusinessProfileResponse;
import com.converter.business.profile.dto.UpsertBusinessProfileRequest;
import com.converter.business.profile.repository.BusinessProfileRepository;
import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Source unique de verite de la distinction PERSONAL/BUSINESS.
 *
 * <p><b>Decision d'architecture</b> : aucune colonne {@code accountType} sur {@code User}. Un
 * utilisateur est {@code BUSINESS} si et seulement si {@link #isBusinessUser} renvoie
 * {@code true} — c'est-a-dire si un {@link BusinessProfile} existe pour lui. Cette regle est
 * encapsulee ici, jamais dispersee en {@code if (businessProfile != null)} dans un controller.
 *
 * <p>Donnee de profil pure : aucune dependance vers {@code Quote}/{@code Order}/{@code Payment}/
 * {@code Settlement}/{@code Treasury}/{@code Wallet}. La creation/mise a jour d'un profil ne
 * declenche jamais rien du pipeline financier.
 *
 * <p><b>Concurrence</b> : deux creations concurrentes pour le meme utilisateur ne peuvent pas
 * toutes les deux reussir — {@code uq_business_profiles_user} (migration V26) le garantit cote
 * PostgreSQL ; la violation qui en resulterait est deja convertie en {@code 409 DUPLICATE_RESOURCE}
 * par {@code GlobalExceptionHandler} (aucun code de conversion supplementaire necessaire ici).
 */
@Service
public class BusinessProfileService {

    private static final Logger log = LoggerFactory.getLogger(BusinessProfileService.class);

    private final BusinessProfileRepository repository;
    private final AuditService auditService;
    private final Clock clock;

    public BusinessProfileService(BusinessProfileRepository repository, AuditService auditService, Clock clock) {
        this.repository = repository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean isBusinessUser(UUID userId) {
        return repository.existsByUserId(userId);
    }

    @Transactional(readOnly = true)
    public BusinessProfileResponse get(UUID userId) {
        BusinessProfile profile = repository.findByUserId(userId).orElseThrow(() -> notFound(userId));
        return toResponse(profile);
    }

    /**
     * {@code PUT} idempotent : cree si absent, met a jour sinon (section 7) — jamais trois
     * operations distinctes pour une ressource singleton par utilisateur. Le propriétaire vient
     * exclusivement de {@code userId} (utilisateur authentifie), jamais du corps de la requete.
     */
    @Transactional
    public UpsertResult upsert(UpsertBusinessProfileRequest request, UUID userId) {
        Instant now = clock.instant();
        var existing = repository.findByUserId(userId);

        if (existing.isPresent()) {
            BusinessProfile profile = existing.get();
            profile.update(request.businessName(), request.businessType(), request.registrationNumber(),
                    request.country(), request.city(), request.address(), now);
            auditService.record(userId, null, AuditAction.BUSINESS_PROFILE_UPDATED, "BusinessProfile",
                    profile.getId().toString(), null);
            log.info("Profil professionnel {} mis a jour pour {}", profile.getId(), userId);
            return new UpsertResult(toResponse(profile), false);
        }

        BusinessProfile created = repository.save(new BusinessProfile(userId, request.businessName(),
                request.businessType(), request.registrationNumber(), request.country(), request.city(),
                request.address(), now));
        auditService.record(userId, null, AuditAction.BUSINESS_PROFILE_CREATED, "BusinessProfile",
                created.getId().toString(), null);
        log.info("Profil professionnel {} cree pour {}", created.getId(), userId);
        return new UpsertResult(toResponse(created), true);
    }

    private BusinessException notFound(UUID userId) {
        return new BusinessException(ErrorCode.BUSINESS_PROFILE_NOT_FOUND,
                "Aucun profil professionnel pour cet utilisateur.");
    }

    private static BusinessProfileResponse toResponse(BusinessProfile profile) {
        return new BusinessProfileResponse(profile.getId(), profile.getBusinessName(), profile.getBusinessType(),
                profile.getRegistrationNumber(), profile.getCountry(), profile.getCity(), profile.getAddress(),
                profile.getCreatedAt(), profile.getUpdatedAt());
    }

    /** {@code created = true} si cet appel vient de creer le profil (201), {@code false} pour une mise a jour (200). */
    public record UpsertResult(BusinessProfileResponse response, boolean created) {
    }
}
