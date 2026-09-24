package com.converter.supplier.web;

import com.converter.common.api.ApiResponse;
import com.converter.common.api.PageResponse;
import com.converter.common.idempotency.IdempotencyGuard;
import com.converter.order.dto.OrderDetailResponse;
import com.converter.security.AuthenticatedUser;
import com.converter.security.CurrentUser;
import com.converter.supplier.domain.SupplierStatus;
import com.converter.supplier.dto.CreateSupplierRequest;
import com.converter.supplier.dto.PayAgainRequest;
import com.converter.supplier.dto.SupplierDetailResponse;
import com.converter.supplier.dto.SupplierSummaryResponse;
import com.converter.supplier.dto.UpdateSupplierRequest;
import com.converter.supplier.service.RepeatPaymentService;
import com.converter.supplier.service.SupplierService;
import com.converter.storage.ProofDownload;
import com.converter.storage.exception.StorageException;
import com.fasterxml.jackson.core.type.TypeReference;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * Carnet de fournisseurs/beneficiaires reutilisables du client. Ownership appliquee comme
 * partout ailleurs : 404, jamais 403, sur le fournisseur d'un autre utilisateur.
 */
@RestController
@RequestMapping("/api/v1/suppliers")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Fournisseurs", description = "Carnet de fournisseurs/beneficiaires reutilisables")
public class SupplierController {

    private final SupplierService supplierService;
    private final RepeatPaymentService repeatPaymentService;
    private final IdempotencyGuard idempotencyGuard;

    public SupplierController(SupplierService supplierService, RepeatPaymentService repeatPaymentService,
                              IdempotencyGuard idempotencyGuard) {
        this.supplierService = supplierService;
        this.repeatPaymentService = repeatPaymentService;
        this.idempotencyGuard = idempotencyGuard;
    }

    @PostMapping
    @Operation(summary = "Enregistrer un fournisseur")
    public ResponseEntity<ApiResponse<SupplierDetailResponse>> create(
            @Valid @RequestBody CreateSupplierRequest request,
            @AuthenticatedUser CurrentUser currentUser) {
        SupplierDetailResponse response = supplierService.create(request, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response, "Fournisseur enregistre."));
    }

    @GetMapping
    @Operation(summary = "Lister mes fournisseurs", description = "Filtrable par statut.")
    public ResponseEntity<ApiResponse<PageResponse<SupplierSummaryResponse>>> list(
            @RequestParam(required = false) SupplierStatus status,
            @AuthenticatedUser CurrentUser currentUser,
            @Parameter(hidden = true) @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.of(supplierService.list(currentUser.getId(), status, pageable)));
    }

    @GetMapping("/favorites")
    @Operation(summary = "Mes fournisseurs favoris")
    public ResponseEntity<ApiResponse<PageResponse<SupplierSummaryResponse>>> listFavorites(
            @AuthenticatedUser CurrentUser currentUser,
            @Parameter(hidden = true) @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.of(supplierService.listFavorites(currentUser.getId(), pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detail d'un fournisseur")
    public ResponseEntity<ApiResponse<SupplierDetailResponse>> get(
            @PathVariable UUID id,
            @AuthenticatedUser CurrentUser currentUser) {
        return ResponseEntity.ok(ApiResponse.of(supplierService.get(id, currentUser.getId())));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Mettre a jour un fournisseur",
            description = "N'affecte jamais un ordre deja cree a partir de ce fournisseur (snapshot immuable).")
    public ResponseEntity<ApiResponse<SupplierDetailResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSupplierRequest request,
            @AuthenticatedUser CurrentUser currentUser) {
        SupplierDetailResponse response = supplierService.update(id, request, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.of(response, "Fournisseur mis a jour."));
    }

    @PostMapping(value = "/{id}/qr-code", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Televerser le code QR Alipay/WeChat du fournisseur",
            description = "Reserve aux fournisseurs ALIPAY/WECHAT_PAY — un compte bancaire chinois s'identifie "
                    + "par accountNumber, jamais par un code QR. Formats acceptes : JPEG, PNG, WEBP, PDF. "
                    + "Remplace le code QR precedent s'il en existait deja un.")
    public ResponseEntity<ApiResponse<SupplierDetailResponse>> uploadQrCode(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @AuthenticatedUser CurrentUser currentUser) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new StorageException("Lecture du fichier envoye impossible.", e);
        }
        SupplierDetailResponse response = supplierService.attachQrCode(id, file.getOriginalFilename(),
                file.getContentType(), content, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.of(response, "Code QR enregistre."));
    }

    @GetMapping("/{id}/qr-code")
    @Operation(summary = "Telecharger le code QR du fournisseur", description = "Reserve au proprietaire.")
    public ResponseEntity<Resource> downloadQrCode(
            @PathVariable UUID id,
            @AuthenticatedUser CurrentUser currentUser) {
        ProofDownload qrCode = supplierService.getQrCode(id, currentUser.getId());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(qrCode.contentType()))
                .body(qrCode.resource());
    }

    @PostMapping("/{id}/favorite")
    @Operation(summary = "Marquer comme favori")
    public ResponseEntity<ApiResponse<SupplierDetailResponse>> favorite(
            @PathVariable UUID id,
            @AuthenticatedUser CurrentUser currentUser) {
        return ResponseEntity.ok(ApiResponse.of(supplierService.setFavorite(id, currentUser.getId(), true)));
    }

    @PostMapping("/{id}/unfavorite")
    @Operation(summary = "Retirer des favoris")
    public ResponseEntity<ApiResponse<SupplierDetailResponse>> unfavorite(
            @PathVariable UUID id,
            @AuthenticatedUser CurrentUser currentUser) {
        return ResponseEntity.ok(ApiResponse.of(supplierService.setFavorite(id, currentUser.getId(), false)));
    }

    @PostMapping("/{id}/deactivate")
    @Operation(summary = "Desactiver un fournisseur",
            description = "Desactivation logique uniquement : aucune suppression reelle, l'historique des ordres deja crees reste intact.")
    public ResponseEntity<ApiResponse<SupplierDetailResponse>> deactivate(
            @PathVariable UUID id,
            @AuthenticatedUser CurrentUser currentUser) {
        return ResponseEntity.ok(ApiResponse.of(supplierService.deactivate(id, currentUser.getId()),
                "Fournisseur desactive."));
    }

    @PostMapping("/{id}/pay-again")
    @Operation(summary = "Payer a nouveau ce fournisseur",
            description = "Cree un NOUVEAU devis (pricing courant) puis un NOUVEL ordre a partir de ce fournisseur "
                    + "— ne copie jamais le taux, les frais ou le montant CNY d'une transaction passee. "
                    + "Le fournisseur doit etre actif. En-tete Idempotency-Key optionnel : un rejeu avec la meme "
                    + "cle et le meme corps ne cree jamais un second ordre.")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> payAgain(
            @PathVariable UUID id,
            @Valid @RequestBody PayAgainRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticatedUser CurrentUser currentUser) {
        // Le fournisseur fait partie de l'identite de l'endpoint (meme principe que
        // PaymentController#submit pour orderId) : la meme cle reutilisee sur un autre
        // fournisseur ne doit jamais rejouer la reponse d'un paiement different.
        String endpoint = "POST /api/v1/suppliers/" + id + "/pay-again";
        return idempotencyGuard.guard(currentUser.getId(), endpoint, idempotencyKey, request,
                new TypeReference<ApiResponse<OrderDetailResponse>>() {
                },
                () -> {
                    OrderDetailResponse response = repeatPaymentService.payAgain(id, request, currentUser.getId());
                    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response, "Ordre cree."));
                });
    }
}
