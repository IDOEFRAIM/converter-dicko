package com.converter.auth;

import com.converter.audit.service.AuditService;
import com.converter.auth.dto.AuthResponse;
import com.converter.auth.dto.CompleteGoogleSignUpRequest;
import com.converter.auth.dto.GoogleSignInRequest;
import com.converter.auth.dto.GoogleSignInResponse;
import com.converter.auth.google.GoogleIdentity;
import com.converter.auth.google.GoogleTokenVerifierService;
import com.converter.common.exception.BusinessException;
import com.converter.common.exception.DuplicateResourceException;
import com.converter.common.exception.UserBlockedException;
import com.converter.security.jwt.JwtService;
import com.converter.user.domain.Role;
import com.converter.user.domain.RoleCode;
import com.converter.user.domain.User;
import com.converter.user.dto.UserResponse;
import com.converter.user.mapper.UserMapper;
import com.converter.user.repository.RoleRepository;
import com.converter.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de la connexion/inscription Google — verificateur simule
 * (jamais de vrai jeton Google ici, voir GoogleTokenVerifierServiceTest pour
 * la partie reellement cryptographique). Couvre en particulier : les deux
 * issues de {@code googleSignIn} (compte existant vs nouveau), l'idempotence
 * de {@code completeGoogleSignUp} sur double soumission, le conflit de
 * telephone deja pris, et la garde ajoutee a {@code login()} pour un compte
 * sans mot de passe (V34).
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceGoogleTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private UserMapper userMapper;
    @Mock
    private AuditService auditService;
    @Mock
    private GoogleTokenVerifierService googleTokenVerifierService;

    private AuthService authService;

    private static final String ID_TOKEN = "fake-google-id-token";
    private static final GoogleIdentity IDENTITY =
            new GoogleIdentity("google-sub-123", "jane@example.com", true, "Jane", "Doe");

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, roleRepository, passwordEncoder, jwtService,
                userMapper, auditService, googleTokenVerifierService);

        lenient().when(googleTokenVerifierService.verify(ID_TOKEN)).thenReturn(IDENTITY);
        lenient().when(jwtService.generateToken(any())).thenReturn("jwt-token");
        lenient().when(jwtService.expirationDuration()).thenReturn(Duration.ofHours(2));
        lenient().when(userMapper.toResponse(any())).thenReturn(dummyUserResponse());
    }

    @Test
    void googleSignIn_noMatchingAccount_returnsNewAccountWithoutCreatingAnything() {
        when(userRepository.findByGoogleSubject(IDENTITY.subject())).thenReturn(Optional.empty());

        GoogleSignInResponse response = authService.googleSignIn(new GoogleSignInRequest(ID_TOKEN));

        assertThat(response.accountExists()).isFalse();
        assertThat(response.auth()).isNull();
        assertThat(response.email()).isEqualTo("jane@example.com");
        assertThat(response.suggestedFirstName()).isEqualTo("Jane");
        assertThat(response.suggestedLastName()).isEqualTo("Doe");
        verify(userRepository, never()).save(any());
    }

    @Test
    void googleSignIn_matchingActiveAccount_logsIn() {
        User existing = googleUser();
        when(userRepository.findByGoogleSubject(IDENTITY.subject())).thenReturn(Optional.of(existing));
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        GoogleSignInResponse response = authService.googleSignIn(new GoogleSignInRequest(ID_TOKEN));

        assertThat(response.accountExists()).isTrue();
        assertThat(response.auth()).isNotNull();
        assertThat(response.auth().accessToken()).isEqualTo("jwt-token");
        verify(userRepository).save(existing);
    }

    @Test
    void googleSignIn_matchingBlockedAccount_throws() {
        User existing = googleUser();
        existing.block("fraude", UUID.randomUUID(), Instant.now());
        when(userRepository.findByGoogleSubject(IDENTITY.subject())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> authService.googleSignIn(new GoogleSignInRequest(ID_TOKEN)))
                .isInstanceOf(UserBlockedException.class);
    }

    @Test
    void completeGoogleSignUp_newPhone_createsAccountWithoutPassword() {
        when(userRepository.findByGoogleSubject(IDENTITY.subject())).thenReturn(Optional.empty());
        when(userRepository.existsByPhone("+22507000000")).thenReturn(false);
        when(roleRepository.findByCode(RoleCode.USER)).thenReturn(Optional.of(mock(Role.class)));
        when(userRepository.save(any())).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        AuthResponse response = authService.completeGoogleSignUp(new CompleteGoogleSignUpRequest(
                ID_TOKEN, "+22507000000", "Jane", "Doe", null));

        assertThat(response.accessToken()).isEqualTo("jwt-token");
        verify(userRepository).save(argThatSavedUserHasNoPassword());
    }

    @Test
    void completeGoogleSignUp_phoneAlreadyTaken_throwsDuplicate() {
        when(userRepository.findByGoogleSubject(IDENTITY.subject())).thenReturn(Optional.empty());
        when(userRepository.existsByPhone("+22507000000")).thenReturn(true);

        assertThatThrownBy(() -> authService.completeGoogleSignUp(new CompleteGoogleSignUpRequest(
                ID_TOKEN, "+22507000000", "Jane", "Doe", null)))
                .isInstanceOf(DuplicateResourceException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void completeGoogleSignUp_alreadyCompletedForThisGoogleAccount_logsInInsteadOfFailing() {
        User existing = googleUser();
        when(userRepository.findByGoogleSubject(IDENTITY.subject())).thenReturn(Optional.of(existing));
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse response = authService.completeGoogleSignUp(new CompleteGoogleSignUpRequest(
                ID_TOKEN, "+22507000000", "Jane", "Doe", null));

        assertThat(response.accessToken()).isEqualTo("jwt-token");
        // Idempotent : aucune tentative de creation, la demande de telephone n'est meme pas consultee.
        verify(userRepository, never()).existsByPhone(any());
    }

    @Test
    void login_googleOnlyAccount_rejectsPhonePasswordWithoutThrowing() {
        User googleOnly = googleUser();
        when(userRepository.findByPhone(eq("+22507000000"))).thenReturn(Optional.of(googleOnly));

        assertThatThrownBy(() -> authService.login(
                new com.converter.auth.dto.LoginRequest("+22507000000", "anything")))
                .isInstanceOf(BusinessException.class);

        verify(passwordEncoder, never()).matches(any(), any());
    }

    private User googleUser() {
        User user = User.googleSignUp("+22507000000", IDENTITY.subject(), IDENTITY.email(), "Jane", "Doe");
        user.setId(UUID.randomUUID());
        return user;
    }

    private static User argThatSavedUserHasNoPassword() {
        return org.mockito.ArgumentMatchers.argThat(user -> user != null && user.getPasswordHash() == null);
    }

    private static UserResponse dummyUserResponse() {
        return new UserResponse(UUID.randomUUID(), "+22507000000", "Jane", "Doe", "jane@example.com",
                com.converter.user.domain.UserStatus.ACTIVE, java.util.Set.of("USER"), Instant.now(), null,
                com.converter.user.domain.ExperienceProfile.PRO, false, false);
    }
}
