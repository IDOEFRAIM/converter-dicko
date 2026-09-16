package com.converter.admin;

import com.converter.common.api.ApiResponse;
import com.converter.common.api.PageResponse;
import com.converter.security.AuthenticatedUser;
import com.converter.security.CurrentUser;
import com.converter.storage.ProofDownload;
import com.converter.supplier.dto.SupplierDetailResponse;
import com.converter.supplier.service.SupplierService;
import com.converter.user.domain.UserStatus;
import com.converter.user.dto.AdminUserDetail;
import com.converter.user.dto.AdminUserSummary;
import com.converter.user.dto.BlockUserRequest;
import com.converter.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Gestion des comptes clients, reservee aux administrateurs.
 *
 * <p>L'acces est deja restreint au niveau du {@code SecurityFilterChain}
 * ({@code /api/admin/**} exige {@code ROLE_ADMIN}) : les annotations
 * {@code @PreAuthorize} ci-dessous forment la seconde barriere,
 * independante, decrite en Phase 1 (section J.2, risque 9).
 */
@RestController
@RequestMapping("/api/admin/users")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Administration - Utilisateurs", description = "Consultation et blocage des comptes clients")
public class AdminUserController {

    private final UserService userService;
    private final SupplierService supplierService;

    public AdminUserController(UserService userService, SupplierService supplierService) {
        this.userService = userService;
        this.supplierService = supplierService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lister les comptes",
            description = "Recherche paginee par statut et par texte libre (nom ou telephone).")
    public ResponseEntity<ApiResponse<PageResponse<AdminUserSummary>>> list(
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String search,
            @Parameter(hidden = true) @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.of(userService.search(status, search, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Detail d'un compte")
    public ResponseEntity<ApiResponse<AdminUserDetail>> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(userService.findById(id)));
    }

    @PostMapping("/{id}/block")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Bloquer un compte",
            description = "Desactive le compte immediatement : toute requete authentifiee "
                    + "ulterieure avec un jeton deja emis pour ce compte sera rejetee "
                    + "(403 USER_BLOCKED), sans attendre l'expiration du jeton.")
    public ResponseEntity<ApiResponse<AdminUserDetail>> block(
            @PathVariable UUID id,
            @Valid @RequestBody BlockUserRequest request,
            @AuthenticatedUser CurrentUser actor) {
        AdminUserDetail result = userService.block(id, request, actor.getId());
        return ResponseEntity.ok(ApiResponse.of(result, "Compte bloque."));
    }

    @PostMapping("/{id}/unblock")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Debloquer un compte")
    public ResponseEntity<ApiResponse<AdminUserDetail>> unblock(
            @PathVariable UUID id,
            @AuthenticatedUser CurrentUser actor) {
        AdminUserDetail result = userService.unblock(id, actor.getId());
        return ResponseEntity.ok(ApiResponse.of(result, "Compte debloque."));
    }

    @PostMapping("/{id}/kyc/verify")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Verifier l'identite (KYC) d'un compte",
            description = "Necessaire pour que ce client puisse creer un ordre au-dela du seuil "
                    + "KYC_REQUIRED_THRESHOLD_XOF. Verification minimale (drapeau administrateur), "
                    + "aucun document/upload/OCR.")
    public ResponseEntity<ApiResponse<AdminUserDetail>> verifyKyc(
            @PathVariable UUID id,
            @AuthenticatedUser CurrentUser actor) {
        AdminUserDetail result = userService.verifyKyc(id, actor.getId());
        return ResponseEntity.ok(ApiResponse.of(result, "Identite verifiee."));
    }

    @PostMapping("/{id}/kyc/revoke")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Revoquer la verification d'identite (KYC) d'un compte")
    public ResponseEntity<ApiResponse<AdminUserDetail>> revokeKyc(
            @PathVariable UUID id,
            @AuthenticatedUser CurrentUser actor) {
        AdminUserDetail result = userService.revokeKyc(id, actor.getId());
        return ResponseEntity.ok(ApiResponse.of(result, "Verification d'identite revoquee."));
    }

    @GetMapping("/{id}/suppliers")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Fournisseurs enregistres par ce client",
            description = "Detail complet (compte en clair, jamais masque comme dans le carnet du client "
                    + "lui-meme) -- c'est ce qui permet a un administrateur d'effectuer ou de verifier un "
                    + "transfert pour le compte de ce client.")
    public ResponseEntity<ApiResponse<PageResponse<SupplierDetailResponse>>> suppliers(
            @PathVariable UUID id,
            @Parameter(hidden = true) @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.of(supplierService.listForAdmin(id, pageable)));
    }

    @GetMapping("/{id}/suppliers/{supplierId}/qr-code")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Code QR d'un fournisseur de ce client",
            description = "Reserve aux fournisseurs ALIPAY/WECHAT_PAY. 404 si ce fournisseur n'appartient "
                    + "pas a ce client ou n'a pas encore de code QR televerse.")
    public ResponseEntity<Resource> supplierQrCode(
            @PathVariable UUID id,
            @PathVariable UUID supplierId) {
        ProofDownload qrCode = supplierService.getQrCodeForAdmin(supplierId, id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(qrCode.contentType()))
                .body(qrCode.resource());
    }
}
