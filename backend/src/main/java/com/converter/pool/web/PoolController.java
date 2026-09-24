package com.converter.pool.web;

import com.converter.common.api.ApiResponse;
import com.converter.common.api.PageResponse;
import com.converter.pool.dto.CreatePoolRequest;
import com.converter.pool.dto.PoolParticipantResponse;
import com.converter.pool.dto.PoolResponse;
import com.converter.pool.service.PoolService;
import com.converter.security.AuthenticatedUser;
import com.converter.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * "Ruee collective" (mission "differenciation marketing", Lot 3). La contribution elle-meme
 * (creation d'un ordre en reference a un pool) passe par {@code POST /api/v1/orders} (champ
 * {@code poolId}), jamais par un endpoint de ce controleur -- un seul chemin de creation d'ordre,
 * jamais duplique.
 */
@RestController
@RequestMapping("/api/v1/pools")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Ruee collective", description = "Defi de conversion collectif, invitation par code court")
public class PoolController {

    private final PoolService poolService;

    public PoolController(PoolService poolService) {
        this.poolService = poolService;
    }

    @PostMapping
    @Operation(summary = "Creer une Ruee", description = "Le createur est automatiquement le premier participant.")
    public ResponseEntity<ApiResponse<PoolResponse>> create(
            @Valid @RequestBody CreatePoolRequest request,
            @AuthenticatedUser CurrentUser currentUser) {
        PoolResponse response = poolService.create(request, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response, "Ruee creee."));
    }

    @GetMapping("/mine")
    @Operation(summary = "Mes Ruees (creees ou rejointes)")
    public ResponseEntity<ApiResponse<PageResponse<PoolResponse>>> mine(
            @AuthenticatedUser CurrentUser currentUser,
            @Parameter(hidden = true) @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.of(poolService.mine(currentUser.getId(), pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detail d'une Ruee", description = "Consultable par tout compte authentifie, meme avant de la rejoindre.")
    public ResponseEntity<ApiResponse<PoolResponse>> get(
            @PathVariable UUID id,
            @AuthenticatedUser CurrentUser currentUser) {
        return ResponseEntity.ok(ApiResponse.of(poolService.get(id, currentUser.getId())));
    }

    @GetMapping("/by-code/{code}")
    @Operation(summary = "Detail d'une Ruee par son code partageable")
    public ResponseEntity<ApiResponse<PoolResponse>> getByCode(
            @PathVariable String code,
            @AuthenticatedUser CurrentUser currentUser) {
        return ResponseEntity.ok(ApiResponse.of(poolService.getByCode(code, currentUser.getId())));
    }

    @GetMapping("/{id}/participants")
    @Operation(summary = "Participants d'une Ruee")
    public ResponseEntity<ApiResponse<List<PoolParticipantResponse>>> participants(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(poolService.participants(id)));
    }

    @PostMapping("/{id}/join")
    @Operation(summary = "Rejoindre une Ruee")
    public ResponseEntity<ApiResponse<PoolResponse>> join(
            @PathVariable UUID id,
            @AuthenticatedUser CurrentUser currentUser) {
        return ResponseEntity.ok(ApiResponse.of(poolService.join(id, currentUser.getId()), "Ruee rejointe."));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Annuler ma Ruee", description = "Reserve au createur, uniquement tant qu'elle est active.")
    public ResponseEntity<ApiResponse<PoolResponse>> cancel(
            @PathVariable UUID id,
            @AuthenticatedUser CurrentUser currentUser) {
        return ResponseEntity.ok(ApiResponse.of(poolService.cancel(id, currentUser.getId()), "Ruee annulee."));
    }
}
