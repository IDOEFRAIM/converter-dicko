package com.converter.user.service;

import com.converter.audit.domain.AuditAction;
import com.converter.audit.service.AuditService;
import com.converter.user.domain.User;
import com.converter.user.dto.AdminUserDetail;
import com.converter.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verification d'identite (KYC) — drapeau administrateur minimal, jamais un moteur de conformite
 * complet. Idempotence testee explicitement : mirroring {@code block}/{@code unblock}.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    private final UUID actorId = UUID.randomUUID();

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, auditService);
    }

    private User unverifiedUser() {
        User user = new User("+22500000001", "hash", "Test", "User");
        user.setId(UUID.randomUUID());
        return user;
    }

    @Test
    void verifyKyc_unverifiedUser_marksVerifiedAndAudits() {
        User user = unverifiedUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        AdminUserDetail result = service.verifyKyc(user.getId(), actorId);

        assertThat(result.kycVerified()).isTrue();
        assertThat(result.kycVerifiedAt()).isNotNull();
        verify(auditService).record(eq(actorId), isNull(), eq(AuditAction.USER_KYC_VERIFIED),
                eq("User"), anyString(), isNull());
    }

    @Test
    void verifyKyc_alreadyVerified_isIdempotent_neverDoubleAudits() {
        User user = unverifiedUser();
        user.verifyKyc(actorId, java.time.Instant.now());
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        AdminUserDetail result = service.verifyKyc(user.getId(), actorId);

        assertThat(result.kycVerified()).isTrue();
        verify(userRepository, never()).save(user);
        verify(auditService, never()).record(eq(actorId), isNull(), eq(AuditAction.USER_KYC_VERIFIED),
                anyString(), anyString(), isNull());
    }

    @Test
    void revokeKyc_verifiedUser_marksUnverifiedAndAudits() {
        User user = unverifiedUser();
        user.verifyKyc(actorId, java.time.Instant.now());
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        AdminUserDetail result = service.revokeKyc(user.getId(), actorId);

        assertThat(result.kycVerified()).isFalse();
        assertThat(result.kycVerifiedAt()).isNull();
        verify(auditService).record(eq(actorId), isNull(), eq(AuditAction.USER_KYC_REVOKED),
                eq("User"), anyString(), isNull());
    }

    @Test
    void revokeKyc_alreadyUnverified_isIdempotent() {
        User user = unverifiedUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        AdminUserDetail result = service.revokeKyc(user.getId(), actorId);

        assertThat(result.kycVerified()).isFalse();
        verify(userRepository, never()).save(user);
        verify(auditService, never()).record(eq(actorId), isNull(), eq(AuditAction.USER_KYC_REVOKED),
                anyString(), anyString(), isNull());
    }
}
