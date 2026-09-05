package com.converter.payment.web;

import com.converter.common.api.ApiResponse;
import com.converter.common.api.PageResponse;
import com.converter.payment.dto.PaymentResponse;
import com.converter.payment.dto.RejectPaymentRequest;
import com.converter.payment.service.PaymentService;
import com.converter.security.AuthenticatedUser;
import com.converter.security.CurrentUser;
import com.converter.storage.ProofDownload;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
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

/** Revue des paiements soumis, reservee a l'administration. */
@RestController
@RequestMapping("/api/admin/payments")
@SecurityRequirement(name = "bearer-jwt")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administration - Paiements", description = "File de verification des paiements soumis")
public class AdminPaymentController {

    private final PaymentService paymentService;

    public AdminPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/pending")
    @Operation(summary = "File d'attente de verification", description = "Le plus ancien en premier.")
    public ResponseEntity<ApiResponse<PageResponse<PaymentResponse>>> pending(
            @Parameter(hidden = true) @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.of(paymentService.pending(pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detail d'un paiement, preuves incluses")
    public ResponseEntity<ApiResponse<PaymentResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(paymentService.adminGet(id)));
    }

    @GetMapping("/{id}/proofs/{proofId}")
    @Operation(summary = "Visualiser une preuve de paiement (administration)",
            description = "Servi avec son type MIME reel (verifie par signature binaire a l'upload) pour permettre "
                    + "un apercu en ligne pendant la verification, jamais l'en-tete brut fourni par le client.")
    public ResponseEntity<Resource> downloadProof(@PathVariable UUID id, @PathVariable UUID proofId) {
        ProofDownload proof = paymentService.adminDownloadProof(id, proofId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(proof.contentType()))
                .body(proof.resource());
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Confirmer un paiement",
            description = "Transitionne l'ordre vers PAYMENT_VERIFIED et enregistre un depot XOF en tresorerie.")
    public ResponseEntity<ApiResponse<PaymentResponse>> confirm(
            @PathVariable UUID id,
            @AuthenticatedUser CurrentUser actor) {
        return ResponseEntity.ok(ApiResponse.of(paymentService.confirm(id, actor.getId()), "Paiement confirme."));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Rejeter un paiement",
            description = "Motif obligatoire. Termine l'ordre (REJECTED) et libere la reservation de tresorerie.")
    public ResponseEntity<ApiResponse<PaymentResponse>> reject(
            @PathVariable UUID id,
            @Valid @RequestBody RejectPaymentRequest request,
            @AuthenticatedUser CurrentUser actor) {
        return ResponseEntity.ok(ApiResponse.of(paymentService.reject(id, request.reason(), actor.getId()),
                "Paiement rejete."));
    }
}
