package com.converter.auth;

import com.converter.audit.domain.AuditAction;
import com.converter.audit.service.AuditService;
import com.converter.auth.dto.AuthResponse;
import com.converter.auth.dto.CompleteGoogleSignUpRequest;
import com.converter.auth.dto.GoogleSignInRequest;
import com.converter.auth.dto.GoogleSignInResponse;
import com.converter.auth.dto.LoginRequest;
import com.converter.auth.dto.RegisterRequest;
import com.converter.auth.google.GoogleIdentity;
import com.converter.auth.google.GoogleTokenVerifierService;
import com.converter.common.exception.BusinessException;
import com.converter.common.exception.DuplicateResourceException;
import com.converter.common.exception.ErrorCode;
import com.converter.common.exception.ResourceNotFoundException;
import com.converter.common.exception.UserBlockedException;
import com.converter.common.validation.PhoneNumberValidator;
import com.converter.security.jwt.JwtService;
import com.converter.user.domain.ExperienceProfile;
import com.converter.user.domain.Role;
import com.converter.user.domain.RoleCode;
import com.converter.user.domain.User;
import com.converter.user.dto.UserResponse;
import com.converter.user.mapper.UserMapper;
import com.converter.user.repository.RoleRepository;
import com.converter.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Inscription et connexion.
 *
 * <p>Le message renvoye en cas d'identifiants invalides est
 * volontairement generique et identique que le numero n'existe pas ou
 * que le mot de passe soit errone : distinguer les deux permettrait a
 * un attaquant d'enumerer les comptes existants par essai successif de
 * numeros.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final AuditService auditService;
    private final GoogleTokenVerifierService googleTokenVerifierService;

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       UserMapper userMapper,
                       AuditService auditService,
                       GoogleTokenVerifierService googleTokenVerifierService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.auditService = auditService;
        this.googleTokenVerifierService = googleTokenVerifierService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String phone = PhoneNumberValidator.normalize(request.phone());

        if (userRepository.existsByPhone(phone)) {
            // Meme motif d'enumeration que pour le login : on pourrait etre
            // tente de renvoyer un message plus discret ici, mais
            // l'inscription doit informer l'utilisateur legitime que son
            // numero est deja pris, sans quoi il ne peut pas recuperer son
            // compte. Le compromis retenu est standard pour un flux
            // d'inscription (a la difference du flux de connexion).
            throw DuplicateResourceException.phone(phone);
        }

        Role userRole = roleRepository.findByCode(RoleCode.USER)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "Role USER absent en base : migration V3 non executee ?"));

        User user = new User(
                phone,
                passwordEncoder.encode(request.password()),
                request.firstName().trim(),
                request.lastName().trim());
        user.addRole(userRole);
        if (request.experienceProfile() != null) {
            user.changeExperienceProfile(request.experienceProfile());
        }

        User saved = userRepository.save(user);

        auditService.record(saved.getId(), saved.getPhone(), AuditAction.USER_REGISTERED,
                "User", saved.getId().toString(), null);
        log.info("Nouveau compte cree : {}", saved.getId());

        return issueAuthResponse(saved);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String phone = PhoneNumberValidator.normalize(request.phone());

        User user = userRepository.findByPhone(phone).orElse(null);

        // getPasswordHash() est null pour un compte cree uniquement via Google
        // Sign-In (V34) : BCryptPasswordEncoder#matches leve une exception sur
        // un condensat null plutot que de renvoyer false, d'ou la garde explicite.
        if (user == null || user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            if (user != null) {
                auditService.record(user.getId(), user.getPhone(), AuditAction.USER_LOGIN_FAILED,
                        "User", user.getId().toString(), null);
            }
            // Message generique : voir la note de classe sur l'enumeration.
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS,
                    "Numero de telephone ou mot de passe incorrect.");
        }

        if (!user.isActive()) {
            auditService.record(user.getId(), user.getPhone(), AuditAction.USER_LOGIN_FAILED,
                    "User", user.getId().toString(), "{\"reason\":\"BLOCKED\"}");
            throw new UserBlockedException();
        }

        user.recordLogin(Instant.now());
        userRepository.save(user);

        auditService.record(user.getId(), user.getPhone(), AuditAction.USER_LOGIN_SUCCESS,
                "User", user.getId().toString(), null);

        return issueAuthResponse(user);
    }

    /**
     * Premiere etape de la connexion Google : verifie le jeton, puis cherche
     * un compte deja associe. Ne cree JAMAIS de compte ici — Google ne
     * transmet pas de numero de telephone, obligatoire sur cette plateforme
     * (KYC, recherche admin, unicite). Voir {@link #completeGoogleSignUp}
     * pour la creation, et la Javadoc de {@link GoogleSignInResponse}.
     */
    @Transactional
    public GoogleSignInResponse googleSignIn(GoogleSignInRequest request) {
        GoogleIdentity identity = googleTokenVerifierService.verify(request.idToken());

        User user = userRepository.findByGoogleSubject(identity.subject()).orElse(null);
        if (user == null) {
            return GoogleSignInResponse.newAccount(identity.email(), identity.givenName(), identity.familyName());
        }

        if (!user.isActive()) {
            auditService.record(user.getId(), user.getPhone(), AuditAction.USER_LOGIN_FAILED,
                    "User", user.getId().toString(), "{\"reason\":\"BLOCKED\"}");
            throw new UserBlockedException();
        }

        user.recordLogin(Instant.now());
        userRepository.save(user);
        auditService.record(user.getId(), user.getPhone(), AuditAction.USER_LOGIN_SUCCESS,
                "User", user.getId().toString(), "{\"method\":\"GOOGLE\"}");
        log.info("Connexion Google reussie : {}", user.getId());

        return GoogleSignInResponse.existingAccount(issueAuthResponse(user));
    }

    /**
     * Cree le compte apres un {@link #googleSignIn} sans correspondance. Le
     * jeton Google est REVERIFIE ici (jamais fait confiance a une identite
     * deja verifiee ailleurs/transmise en clair) : voir la Javadoc de
     * {@link CompleteGoogleSignUpRequest}.
     */
    @Transactional
    public AuthResponse completeGoogleSignUp(CompleteGoogleSignUpRequest request) {
        GoogleIdentity identity = googleTokenVerifierService.verify(request.idToken());

        // Double soumission (retry reseau, deux onglets...) du MEME compte
        // Google entre-temps deja finalise : traite comme une connexion,
        // jamais comme une erreur -- idempotent du point de vue du client.
        User existing = userRepository.findByGoogleSubject(identity.subject()).orElse(null);
        if (existing != null) {
            existing.recordLogin(Instant.now());
            userRepository.save(existing);
            auditService.record(existing.getId(), existing.getPhone(), AuditAction.USER_LOGIN_SUCCESS,
                    "User", existing.getId().toString(), "{\"method\":\"GOOGLE\"}");
            return issueAuthResponse(existing);
        }

        String phone = PhoneNumberValidator.normalize(request.phone());
        if (userRepository.existsByPhone(phone)) {
            // Meme motif d'enumeration que register() : voir sa Javadoc de methode.
            throw DuplicateResourceException.phone(phone);
        }

        Role userRole = roleRepository.findByCode(RoleCode.USER)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "Role USER absent en base : migration V3 non executee ?"));

        User user = User.googleSignUp(phone, identity.subject(), identity.email(),
                request.firstName().trim(), request.lastName().trim());
        user.addRole(userRole);
        if (request.experienceProfile() != null) {
            user.changeExperienceProfile(request.experienceProfile());
        }

        User saved = userRepository.save(user);

        auditService.record(saved.getId(), saved.getPhone(), AuditAction.USER_REGISTERED,
                "User", saved.getId().toString(), "{\"method\":\"GOOGLE\"}");
        log.info("Nouveau compte cree via Google : {}", saved.getId());

        return issueAuthResponse(saved);
    }

    private AuthResponse issueAuthResponse(User user) {
        String token = jwtService.generateToken(user);
        return AuthResponse.of(token, jwtService.expirationDuration().toSeconds(), userMapper.toResponse(user));
    }

    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.user(userId));
        return userMapper.toResponse(user);
    }

    /**
     * Auto-selectionnable par le client lui-meme : contrairement au KYC, aucun controle
     * administrateur n'entoure ce choix, purement cosmetique (voir {@link
     * com.converter.user.domain.ExperienceProfile}).
     */
    @Transactional
    public UserResponse updateExperienceProfile(UUID userId, ExperienceProfile profile) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.user(userId));

        user.changeExperienceProfile(profile);
        User saved = userRepository.save(user);

        auditService.record(userId, saved.getPhone(), AuditAction.USER_EXPERIENCE_PROFILE_CHANGED,
                "User", userId.toString(), "{\"experienceProfile\":\"" + profile + "\"}");
        log.info("Profil d'experience de {} change en {}", userId, profile);

        return userMapper.toResponse(saved);
    }
}
