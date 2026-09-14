package com.converter.settlement.web;

import com.converter.common.api.ApiResponse;
import com.converter.common.api.PageResponse;
import com.converter.common.idempotency.IdempotencyGuard;
import com.converter.security.AuthenticatedUser;
import com.converter.security.CurrentUser;
import com.converter.settlement.dto.ExecuteSettlementRequest;
import com.converter.settlement.dto.SettlementProofResponse;
import com.converter.settlement.dto.SettlementResponse;
import com.converter.settlement.service.SettlementService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Reglement CNY, reserve a l'administration. Le decaissement reste
 * entierement manuel : cette API structure et trace l'execution, elle
 * ne la realise jamais elle-meme (aucune integration Binance, OKX,
 * P2P, API bancaire chinoise).
 */
@RestController
@RequestMapping("/api/admin")
@SecurityRequirement(name = "bearer-jwt")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administration - Reglements", description = "Execution manuelle du decaissement CNY")
public class AdminSettlementController {

    private final SettlementService settlementService;
    private final IdempotencyGuard idempotencyGuard;

    public AdminSettlementController(SettlementService settlementService, IdempotencyGuard idempotencyGuard) {
        this.settlementService = settlementService;
        this.idempotencyGuard = idempotencyGuard;
    }

    @PostMapping("/orders/{orderId}/settlement")
    @Operation(summary = "Creer le reglement d'un ordre",
            description = "Requiert un ordre au statut PAYMENT_VERIFIED. Fait passer l'ordre en "
                    + "PROCESSING dans la meme operation (\"Payment CONFIRMED -> Settlement cree\"). "
                    + "En-tete Idempotency-Key optionnel : un rejeu avec la meme cle (reponse perdue, "
                    + "double clic) renvoie le reglement deja cree au lieu du 409 \"existe deja\".")
    public ResponseEntity<ApiResponse<SettlementResponse>> create(
            @PathVariable UUID orderId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticatedUser CurrentUser actor) {
        String endpoint = "POST /api/admin/orders/" + orderId + "/settlement";
        return idempotencyGuard.guard(actor.getId(), endpoint, idempotencyKey, Map.of("action", "create"),
                new TypeReference<ApiResponse<SettlementResponse>>() {
                },
                () -> {
                    SettlementResponse response = settlementService.create(orderId, actor.getId());
                    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response, "Reglement cree."));
                });
    }

    @GetMapping("/settlements/pending")
    @Operation(summary = "File des reglements en attente d'execution")
    public ResponseEntity<ApiResponse<PageResponse<SettlementResponse>>> pending(
            @Parameter(hidden = true) @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.of(settlementService.pending(pageable)));
    }

    @GetMapping("/settlements/{id}")
    @Operation(summary = "Detail d'un reglement")
    public ResponseEntity<ApiResponse<SettlementResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(settlementService.get(id)));
    }

    @PostMapping(value = "/settlements/{id}/proofs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Televerser une preuve de reglement")
    public ResponseEntity<ApiResponse<SettlementProofResponse>> uploadProof(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @AuthenticatedUser CurrentUser actor) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new StorageException("Lecture du fichier envoye impossible.", e);
        }
        SettlementProofResponse response = settlementService.uploadProof(id, file.getOriginalFilename(),
                file.getContentType(), content, actor.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response, "Preuve enregistree."));
    }

    @GetMapping("/settlements/{id}/proofs/{proofId}")
    @Operation(summary = "Visualiser une preuve de reglement",
            description = "Servi avec son type MIME reel (verifie par signature binaire a l'upload) pour permettre "
                    + "un apercu en ligne, jamais l'en-tete brut fourni par le client.")
    public ResponseEntity<Resource> downloadProof(@PathVariable UUID id, @PathVariable UUID proofId) {
        ProofDownload proof = settlementService.downloadProof(id, proofId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(proof.contentType()))
                .body(proof.resource());
    }

    @PostMapping("/settlements/{id}/execute")
    @Operation(summary = "Executer un reglement",
            description = "Reference obligatoire. Consomme la reservation de tresorerie CNY et "
                    + "fait passer l'ordre en COMPLETED. En-tete Idempotency-Key optionnel : "
                    + "un rejeu avec la meme cle et le meme corps ne consomme jamais la tresorerie "
                    + "une seconde fois.")
    public ResponseEntity<ApiResponse<SettlementResponse>> execute(
            @PathVariable UUID id,
            @Valid @RequestBody ExecuteSettlementRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticatedUser CurrentUser actor) {
        String endpoint = "POST /api/admin/settlements/" + id + "/execute";
        return idempotencyGuard.guard(actor.getId(), endpoint, idempotencyKey, request,
                new TypeReference<ApiResponse<SettlementResponse>>() {
                },
                () -> {
                    SettlementResponse response = settlementService.execute(id, request.settlementReference(),
                            request.notes(), actor.getId());
                    return ResponseEntity.ok(ApiResponse.of(response, "Reglement execute."));
                });
    }
}
