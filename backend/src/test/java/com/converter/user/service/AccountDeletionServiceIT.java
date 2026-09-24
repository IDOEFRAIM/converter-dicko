package com.converter.user.service;

import com.converter.common.exception.BusinessException;
import com.converter.kyc.domain.KycDocumentType;
import com.converter.kyc.dto.KycFileUpload;
import com.converter.kyc.repository.KycSubmissionRepository;
import com.converter.kyc.service.KycService;
import com.converter.order.dto.OrderDetailResponse;
import com.converter.quote.dto.QuoteResponse;
import com.converter.storage.FileStorageService;
import com.converter.support.AbstractOrderPipelineIT;
import com.converter.user.domain.RoleCode;
import com.converter.user.domain.User;
import com.converter.user.domain.UserStatus;
import com.converter.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Suppression de compte en libre-service (retour client : "est-ce que l'utilisateur a la
 * possibilite de supprimer ses donnees ?"). Anonymisation verifiee (jamais un DELETE de la ligne
 * elle-meme, voir la Javadoc de {@link AccountDeletionService}).
 */
class AccountDeletionServiceIT extends AbstractOrderPipelineIT {

    @Autowired
    private AccountDeletionService accountDeletionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private KycService kycService;

    @Autowired
    private KycSubmissionRepository kycSubmissionRepository;

    @Autowired
    private FileStorageService fileStorageService;

    @Test
    void deleteOwnAccount_anonymizesUserAndFreesThePhoneNumber() {
        User user = createUser(RoleCode.USER);
        String originalPhone = user.getPhone();

        accountDeletionService.deleteOwnAccount(user.getId(), "irrelevant-for-tests");

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(UserStatus.DELETED);
        assertThat(reloaded.getDeletedAt()).isNotNull();
        assertThat(reloaded.getPhone()).isNotEqualTo(originalPhone);
        assertThat(reloaded.getFirstName()).isEqualTo("Compte");
        assertThat(reloaded.getLastName()).isEqualTo("supprime");
        assertThat(reloaded.getEmail()).isNull();
        // Le numero d'origine doit etre reellement libere pour une nouvelle inscription.
        assertThat(userRepository.existsByPhone(originalPhone)).isFalse();
    }

    @Test
    void deleteOwnAccount_wrongPassword_throwsInvalidCredentials() {
        User user = createUser(RoleCode.USER);

        assertThatThrownBy(() -> accountDeletionService.deleteOwnAccount(user.getId(), "mot-de-passe-errone"))
                .isInstanceOf(BusinessException.class);

        assertThat(userRepository.findById(user.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void deleteOwnAccount_missingPassword_throwsInvalidCredentials() {
        User user = createUser(RoleCode.USER);

        assertThatThrownBy(() -> accountDeletionService.deleteOwnAccount(user.getId(), null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deleteOwnAccount_refusesForAdmin() {
        User admin = createUser(RoleCode.ADMIN);

        assertThatThrownBy(() -> accountDeletionService.deleteOwnAccount(admin.getId(), "irrelevant-for-tests"))
                .isInstanceOf(BusinessException.class);

        assertThat(userRepository.findById(admin.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void deleteOwnAccount_blockedWhileAnOrderIsStillOpen() {
        String admin = adminToken();
        resetMarginToZero();
        publishRate(admin, "85.000000");
        depositCny(admin, "1000000");
        User user = createUser(RoleCode.USER);
        String userToken = tokenFor(user);
        QuoteResponse quote = createAcceptedQuote(userToken, "100000");
        OrderDetailResponse order = createOrder(userToken, quote.id(), alipayBeneficiary());
        assertThat(order.id()).isNotNull();

        assertThatThrownBy(() -> accountDeletionService.deleteOwnAccount(user.getId(), "irrelevant-for-tests"))
                .isInstanceOf(BusinessException.class);

        assertThat(userRepository.findById(user.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    /** En-tete JPEG reel (FileValidator verifie les octets, pas seulement le Content-Type declare). */
    private static byte[] fakeJpegBytes(String marker) {
        byte[] header = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
        byte[] body = marker.getBytes();
        byte[] full = new byte[header.length + body.length];
        System.arraycopy(header, 0, full, 0, header.length);
        System.arraycopy(body, 0, full, header.length, body.length);
        return full;
    }

    @Test
    void deleteOwnAccount_purgesKycSubmissionsAndTheirFiles() {
        User user = createUnverifiedUser(RoleCode.USER);
        KycFileUpload front = new KycFileUpload("front.jpg", "image/jpeg", fakeJpegBytes("front"));
        KycFileUpload back = new KycFileUpload("back.jpg", "image/jpeg", fakeJpegBytes("back"));
        KycFileUpload selfie = new KycFileUpload("selfie.jpg", "image/jpeg", fakeJpegBytes("selfie"));
        kycService.submit(user.getId(), KycDocumentType.NATIONAL_ID, front, back, selfie);

        List<UUID> submissionIds = kycSubmissionRepository.findByUserId(user.getId())
                .stream().map(s -> s.getId()).toList();
        assertThat(submissionIds).isNotEmpty();
        String frontKey = kycSubmissionRepository.findById(submissionIds.get(0)).orElseThrow().getDocFrontKey();

        accountDeletionService.deleteOwnAccount(user.getId(), "irrelevant-for-tests");

        assertThat(kycSubmissionRepository.findByUserId(user.getId())).isEmpty();
        assertThatThrownBy(() -> fileStorageService.load(frontKey)).isInstanceOf(BusinessException.class);
    }
}
