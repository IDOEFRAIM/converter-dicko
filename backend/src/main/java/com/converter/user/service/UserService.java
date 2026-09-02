package com.converter.user.service;

import com.converter.audit.domain.AuditAction;
import com.converter.audit.service.AuditService;
import com.converter.common.api.PageResponse;
import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.common.exception.ResourceNotFoundException;
import com.converter.user.domain.User;
import com.converter.user.domain.UserStatus;
import com.converter.user.dto.AdminUserDetail;
import com.converter.user.dto.AdminUserSummary;
import com.converter.user.dto.BlockUserRequest;
import com.converter.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Gestion des comptes cote administration : consultation, blocage,
 * deblocage.
 *
 * <p>{@code orderCount} et {@code totalAmountCfa} sont fixes a zero
 * dans cette phase : le module {@code order} n'existe pas encore
 * (livre en Phase 4). Les DTO exposent deja ces champs afin que le
 * frontend administrateur puisse etre construit contre un contrat
 * stable, sans rupture lorsque les ordres seront branches.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final AuditService auditService;

    public UserService(UserRepository userRepository, AuditService auditService) {
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserSummary> search(UserStatus status, String search, Pageable pageable) {
        Page<User> page = userRepository.search(status, likePattern(search), pageable);
        return PageResponse.from(page, UserService::toSummary);
    }

    @Transactional(readOnly = true)
    public AdminUserDetail findById(UUID id) {
        User user = userRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.user(id));
        return toDetail(user);
    }

    @Transactional
    public AdminUserDetail block(UUID id, BlockUserRequest request, UUID actorId) {
        User user = userRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.user(id));

        if (user.getId().equals(actorId)) {
            // Un administrateur ne doit jamais pouvoir se retirer lui-meme
            // l'acces : cela laisserait potentiellement la plateforme sans
            // administrateur actif si c'etait le dernier.
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Un administrateur ne peut pas se bloquer lui-meme.");
        }

        if (user.isBlocked()) {
            // Idempotent plutot qu'une erreur : bloquer un compte deja
            // bloque ne doit pas etre traite comme un echec par le client
            // admin qui rejoue l'action apres un timeout reseau.
            return toDetail(user);
        }

        user.block(request.reason(), actorId, Instant.now());
        User saved = userRepository.save(user);

        auditService.record(actorId, null, AuditAction.USER_BLOCKED,
                "User", id.toString(), "{\"reason\":" + jsonString(request.reason()) + "}");
        log.info("Compte {} bloque par {} : {}", id, actorId, request.reason());

        return toDetail(saved);
    }

    @Transactional
    public AdminUserDetail unblock(UUID id, UUID actorId) {
        User user = userRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.user(id));

        if (user.isActive()) {
            return toDetail(user);
        }

        user.unblock();
        User saved = userRepository.save(user);

        auditService.record(actorId, null, AuditAction.USER_UNBLOCKED,
                "User", id.toString(), null);
        log.info("Compte {} debloque par {}", id, actorId);

        return toDetail(saved);
    }

    private static AdminUserSummary toSummary(User user) {
        return new AdminUserSummary(
                user.getId(),
                user.getPhone(),
                user.fullName(),
                user.getStatus(),
                user.getCreatedAt(),
                0L,
                BigDecimal.ZERO);
    }

    private static AdminUserDetail toDetail(User user) {
        return new AdminUserDetail(
                user.getId(),
                user.getPhone(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getStatus(),
                user.getRoles().stream().map(role -> role.getCode().name())
                        .collect(Collectors.toSet()),
                user.getCreatedAt(),
                user.getLastLoginAt(),
                user.getBlockedAt(),
                user.getBlockedReason(),
                0L,
                BigDecimal.ZERO);
    }

    /**
     * Construit un motif LIKE toujours non nul.
     *
     * <p>"%" (sans terme) filtre sur "tout", ce qui reproduit
     * exactement le comportement d'une recherche non filtree — voir la
     * note de {@link com.converter.user.repository.UserRepository#search}
     * sur la raison de ne jamais transmettre {@code null} ici.
     */
    private static String likePattern(String search) {
        String trimmed = (search == null) ? null : search.trim();
        return (trimmed == null || trimmed.isEmpty()) ? "%" : "%" + trimmed + "%";
    }

    private static String jsonString(String raw) {
        return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
