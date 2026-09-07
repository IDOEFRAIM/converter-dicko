package com.converter.auth;

import com.converter.audit.domain.AuditAction;
import com.converter.audit.service.AuditService;
import com.converter.auth.dto.AuthResponse;
import com.converter.auth.dto.LoginRequest;
import com.converter.auth.dto.RegisterRequest;
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

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       UserMapper userMapper,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.auditService = auditService;
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

        String token = jwtService.generateToken(saved);
        return AuthResponse.of(token, jwtService.expirationDuration().toSeconds(),
                userMapper.toResponse(saved));
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String phone = PhoneNumberValidator.normalize(request.phone());

        User user = userRepository.findByPhone(phone).orElse(null);

        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
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

        String token = jwtService.generateToken(user);
        return AuthResponse.of(token, jwtService.expirationDuration().toSeconds(),
                userMapper.toResponse(user));
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
