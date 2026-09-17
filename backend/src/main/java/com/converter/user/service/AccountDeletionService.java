package com.converter.user.service;

import com.converter.audit.domain.AuditAction;
import com.converter.audit.service.AuditService;
import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.common.exception.ResourceNotFoundException;
import com.converter.kyc.domain.KycSubmission;
import com.converter.kyc.repository.KycSubmissionRepository;
import com.converter.order.repository.OrderRepository;
import com.converter.storage.FileStorageService;
import com.converter.user.domain.RoleCode;
import com.converter.user.domain.User;
import com.converter.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Suppression de compte en libre-service (retour client : "est-ce que
 * l'utilisateur a la possibilite de supprimer ses donnees dans
 * l'application ?").
 *
 * <p>Anonymise, ne supprime JAMAIS la ligne {@code users} elle-meme : les
 * tables financieres (orders, payments, settlements...) referencent cet
 * utilisateur par {@code user_id} et doivent rester consultables pour les
 * obligations legales de conservation (lutte anti-blanchiment — voir la
 * politique de confidentialite publiee, section 6, et {@code
 * V40__account_deletion.sql}). Seules les donnees personnelles
 * identifiantes sont effacees : nom, telephone, email, mot de passe/
 * identifiant Google (voir {@code User#anonymizeForDeletion}), et les
 * fichiers KYC (piece d'identite, selfie) sur le stockage de fichiers.
 */
@Service
public class AccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final KycSubmissionRepository kycSubmissionRepository;
    private final FileStorageService fileStorageService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final Clock clock;

    public AccountDeletionService(UserRepository userRepository,
                                  OrderRepository orderRepository,
                                  KycSubmissionRepository kycSubmissionRepository,
                                  FileStorageService fileStorageService,
                                  PasswordEncoder passwordEncoder,
                                  AuditService auditService,
                                  Clock clock) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.kycSubmissionRepository = kycSubmissionRepository;
        this.fileStorageService = fileStorageService;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public void deleteOwnAccount(UUID userId, String currentPasswordOrNull) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.user(userId));

        if (user.hasRole(RoleCode.ADMIN)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "Un compte administrateur ne peut pas etre supprime depuis cet endpoint.");
        }

        // Un mot de passe pose (compte phone+mot de passe) doit etre re-confirme -- la seule
        // preuve de possession que ce backend sait demander sans flux OTP dedie. Un compte
        // Google-only (passwordHash == null) n'en a pas : l'ecran mobile impose alors une simple
        // confirmation destructive, sans champ mot de passe (voir DeleteAccountRequest).
        if (user.getPasswordHash() != null
                && (currentPasswordOrNull == null
                    || !passwordEncoder.matches(currentPasswordOrNull, user.getPasswordHash()))) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Mot de passe incorrect.");
        }

        if (orderRepository.countOpenOrdersByUser(userId) > 0) {
            throw new BusinessException(ErrorCode.ACCOUNT_DELETION_BLOCKED,
                    "Un ou plusieurs transferts sont encore en cours : ils doivent etre "
                            + "termines, annules ou rejetes avant de pouvoir supprimer ce compte.");
        }

        purgeKycSubmissions(userId);

        String syntheticPhone = userRepository.nextDeletedAccountPhone();
        String unusablePasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
        user.anonymizeForDeletion(syntheticPhone, unusablePasswordHash, clock.instant());
        userRepository.save(user);

        auditService.record(userId, null, AuditAction.ACCOUNT_DELETED, "User", userId.toString(), null);
        log.info("Compte {} supprime (anonymise) a la demande de son titulaire.", userId);
    }

    /**
     * Purge les fichiers (recto/verso/selfie) de CHAQUE dossier KYC de cet utilisateur, rejetes
     * et resoumis inclus, puis les lignes elles-memes -- sans objet une fois leurs fichiers
     * disparus, et {@code User#isKycVerified} (conserve, voir la Javadoc de classe) reste la seule
     * trace utile pour l'historique de conformite.
     */
    private void purgeKycSubmissions(UUID userId) {
        List<KycSubmission> submissions = kycSubmissionRepository.findByUserId(userId);
        for (KycSubmission submission : submissions) {
            deleteFileQuietly(submission.getDocFrontKey());
            deleteFileQuietly(submission.getDocBackKey());
            deleteFileQuietly(submission.getSelfieKey());
        }
        kycSubmissionRepository.deleteAll(submissions);
    }

    private void deleteFileQuietly(String storageKey) {
        if (storageKey == null) {
            return;
        }
        try {
            fileStorageService.delete(storageKey);
        } catch (RuntimeException ex) {
            // Un fichier deja absent/illisible ne doit jamais faire echouer la suppression du
            // compte lui-meme -- l'anonymisation des donnees en base est la garantie essentielle ;
            // un fichier orphelin residuel (rarissime) est un moindre risque que bloquer
            // l'utilisateur dans sa demande de suppression.
            log.warn("Echec de suppression du fichier KYC {} pendant la suppression de compte.", storageKey, ex);
        }
    }
}
