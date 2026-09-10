package com.converter.kyc.service;

import com.converter.audit.domain.AuditAction;
import com.converter.audit.service.AuditService;
import com.converter.common.api.PageResponse;
import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.common.exception.ResourceNotFoundException;
import com.converter.kyc.domain.KycDocumentType;
import com.converter.kyc.domain.KycSubmission;
import com.converter.kyc.domain.KycSubmissionStatus;
import com.converter.kyc.dto.KycAdminSubmissionResponse;
import com.converter.kyc.dto.KycFileUpload;
import com.converter.kyc.dto.KycSubmissionResponse;
import com.converter.kyc.repository.KycSubmissionRepository;
import com.converter.storage.FileStorageService;
import com.converter.storage.FileValidator;
import com.converter.storage.StoredFile;
import com.converter.user.domain.User;
import com.converter.user.repository.UserRepository;
import com.converter.user.service.UserService;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Parcours utilisateur de verification d'identite (remarque produit #6).
 *
 * <p><b>Revue manuelle interne</b> : aucun prestataire tiers, aucun OCR. L'utilisateur televerse
 * une piece (+ verso si applicable) et un selfie ; un administrateur approuve ou rejette.
 * L'approbation delegue a {@link UserService#verifyKyc} — {@code users.kyc_verified} reste
 * l'unique source de verite consommee par {@code OrderService} (jamais deux logiques de gate).
 *
 * <p>Les fichiers passent par {@link FileValidator} (allowlist image/PDF, magic bytes) puis
 * {@link FileStorageService} (repertoire {@code "kyc"}) — jamais stockes en base.
 */
@Service
public class KycService {

    private static final String DIRECTORY = "kyc";
    private static final long MAX_FILE_BYTES = 8L * 1024 * 1024;

    private final KycSubmissionRepository submissionRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final FileStorageService fileStorageService;
    private final FileValidator fileValidator;
    private final AuditService auditService;
    private final Clock clock;

    public KycService(KycSubmissionRepository submissionRepository,
                      UserRepository userRepository,
                      UserService userService,
                      FileStorageService fileStorageService,
                      FileValidator fileValidator,
                      AuditService auditService,
                      Clock clock) {
        this.submissionRepository = submissionRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.fileStorageService = fileStorageService;
        this.fileValidator = fileValidator;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public KycSubmissionResponse submit(UUID userId, KycDocumentType documentType,
                                        KycFileUpload front, KycFileUpload back, KycFileUpload selfie) {
        User user = userRepository.findById(userId).orElseThrow(() -> ResourceNotFoundException.user(userId));
        if (user.isKycVerified()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Votre identite est deja verifiee.");
        }
        if (submissionRepository.existsByUserIdAndStatus(userId, KycSubmissionStatus.PENDING)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Un dossier est deja en cours d'examen. Attendez la reponse avant d'en soumettre un autre.");
        }
        if (front == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Le recto de la piece est requis.");
        }
        if (selfie == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Le selfie est requis.");
        }
        if (documentType != KycDocumentType.PASSPORT && back == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Le verso de la piece est requis pour ce type.");
        }

        String frontKey = storeChecked(front);
        String backKey = back == null ? null : storeChecked(back);
        String selfieKey = storeChecked(selfie);

        KycSubmission submission = submissionRepository.save(new KycSubmission(
                userId, documentType, frontKey, backKey, selfieKey, clock.instant()));
        auditService.record(userId, user.getPhone(), AuditAction.KYC_SUBMITTED, "KycSubmission",
                submission.getId().toString(), "{\"documentType\":\"" + documentType + "\"}");
        return KycSubmissionResponse.of(submission);
    }

    private String storeChecked(KycFileUpload upload) {
        String detected = fileValidator.validate(upload.contentType(), upload.content(), MAX_FILE_BYTES);
        StoredFile stored = fileStorageService.store(DIRECTORY, upload.originalName(), detected, upload.content());
        return stored.storageKey();
    }

    @Transactional(readOnly = true)
    public Optional<KycSubmissionResponse> mySubmission(UUID userId) {
        return submissionRepository.findFirstByUserIdOrderBySubmittedAtDesc(userId).map(KycSubmissionResponse::of);
    }

    @Transactional(readOnly = true)
    public PageResponse<KycAdminSubmissionResponse> pending(Pageable pageable) {
        Page<KycSubmission> page = submissionRepository.findByStatusOrderBySubmittedAtAsc(
                KycSubmissionStatus.PENDING, pageable);
        Map<UUID, User> users = userRepository.findAllById(
                        page.getContent().stream().map(KycSubmission::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, user -> user));
        return PageResponse.from(page, submission -> {
            User user = users.get(submission.getUserId());
            return new KycAdminSubmissionResponse(
                    submission.getId(), submission.getUserId(),
                    user == null ? "?" : user.fullName(),
                    user == null ? "?" : user.getPhone(),
                    submission.getStatus(), submission.getDocumentType(),
                    submission.getDocBackKey() != null, submission.getSubmittedAt());
        });
    }

    @Transactional
    public void approve(UUID submissionId, UUID adminId) {
        KycSubmission submission = requirePending(submissionId);
        submission.approve(adminId, clock.instant());
        submissionRepository.save(submission);
        // Pose users.kyc_verified + audit USER_KYC_VERIFIED. Idempotent cote UserService.
        userService.verifyKyc(submission.getUserId(), adminId);
    }

    @Transactional
    public void reject(UUID submissionId, UUID adminId, String reason) {
        KycSubmission submission = requirePending(submissionId);
        submission.reject(adminId, clock.instant(), reason);
        submissionRepository.save(submission);
        auditService.record(adminId, null, AuditAction.KYC_REJECTED, "KycSubmission",
                submissionId.toString(), null);
    }

    @Transactional(readOnly = true)
    public Resource loadFile(UUID submissionId, String kind) {
        KycSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> notFound(submissionId));
        String key = switch (kind) {
            case "front" -> submission.getDocFrontKey();
            case "back" -> submission.getDocBackKey();
            case "selfie" -> submission.getSelfieKey();
            default -> null;
        };
        if (key == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Fichier KYC introuvable.");
        }
        return fileStorageService.load(key);
    }

    private KycSubmission requirePending(UUID submissionId) {
        KycSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> notFound(submissionId));
        if (!submission.isPending()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Ce dossier a deja ete traite.");
        }
        return submission;
    }

    private BusinessException notFound(UUID submissionId) {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Dossier KYC introuvable : " + submissionId);
    }
}
