package com.converter.kyc.web;

import com.converter.common.api.ApiResponse;
import com.converter.common.api.PageResponse;
import com.converter.kyc.dto.KycAdminSubmissionResponse;
import com.converter.kyc.dto.KycFileDownload;
import com.converter.kyc.dto.RejectKycRequest;
import com.converter.kyc.service.KycService;
import com.converter.security.AuthenticatedUser;
import com.converter.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Revue manuelle des dossiers KYC, reservee aux administrateurs (remarque produit #6). Acces deja
 * restreint au niveau du {@code SecurityFilterChain} ({@code /api/admin/**} exige {@code ROLE_ADMIN}) ;
 * les {@code @PreAuthorize} forment la seconde barriere, comme {@code AdminUserController}.
 */
@RestController
@RequestMapping("/api/admin/kyc")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Administration - KYC", description = "Revue des dossiers de verification d'identite")
public class AdminKycController {

    private final KycService kycService;

    public AdminKycController(KycService kycService) {
        this.kycService = kycService;
    }

    @GetMapping("/submissions")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lister les dossiers KYC en attente d'examen (les plus anciens d'abord)")
    public ResponseEntity<ApiResponse<PageResponse<KycAdminSubmissionResponse>>> pending(
            @Parameter(hidden = true) @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.of(kycService.pending(pageable)));
    }

    @PostMapping("/submissions/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Approuver un dossier KYC",
            description = "Marque le compte comme verifie (meme effet que la voie admin directe).")
    public ResponseEntity<ApiResponse<Void>> approve(
            @PathVariable UUID id,
            @AuthenticatedUser CurrentUser actor) {
        kycService.approve(id, actor.getId());
        return ResponseEntity.ok(ApiResponse.<Void>of(null, "Dossier approuve."));
    }

    @PostMapping("/submissions/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Rejeter un dossier KYC", description = "Un motif est toujours exige.")
    public ResponseEntity<ApiResponse<Void>> reject(
            @PathVariable UUID id,
            @Valid @RequestBody RejectKycRequest request,
            @AuthenticatedUser CurrentUser actor) {
        kycService.reject(id, actor.getId(), request.reason());
        return ResponseEntity.ok(ApiResponse.<Void>of(null, "Dossier rejete."));
    }

    @GetMapping("/submissions/{id}/files/{kind}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Recuperer un fichier d'un dossier KYC",
            description = "kind : front | back | selfie. Type MIME reel (image / PDF), jamais mis en cache, "
                    + "jamais sniffe au-dela de ce type.")
    public ResponseEntity<Resource> file(@PathVariable UUID id, @PathVariable String kind) {
        KycFileDownload download = kycService.loadFile(id, kind);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .header("Cache-Control", "no-store")
                .body(download.resource());
    }
}
