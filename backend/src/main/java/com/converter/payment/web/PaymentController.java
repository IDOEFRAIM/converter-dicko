package com.converter.payment.web;

import com.converter.common.api.ApiResponse;
import com.converter.common.idempotency.IdempotencyGuard;
import com.converter.payment.dto.PaymentProofResponse;
import com.converter.payment.dto.PaymentResponse;
import com.converter.payment.dto.SubmitPaymentRequest;
import com.converter.payment.service.PaymentService;
import com.converter.security.AuthenticatedUser;
import com.converter.security.CurrentUser;
import com.converter.storage.exception.StorageException;
import com.fasterxml.jackson.core.type.TypeReference;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import java.util.UUID;

/**
 * Declaration client d'un paiement XOF et de sa preuve. MVP entierement
 * manuel : aucun appel vers un fournisseur de paiement reel.
 */
@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Paiements", description = "Declaration du paiement XOF et de sa preuve")
public class PaymentController {

    private final PaymentService paymentService;
    private final IdempotencyGuard idempotencyGuard;

    public PaymentController(PaymentService paymentService, IdempotencyGuard idempotencyGuard) {
        this.paymentService = paymentService;
        this.idempotencyGuard = idempotencyGuard;
    }

    @PostMapping("/orders/{orderId}/payments")
    @Operation(summary = "Declarer le paiement d'un ordre",
            description = "Uniquement depuis AWAITING_PAYMENT. Transitionne l'ordre vers PAYMENT_SUBMITTED. "
                    + "En-tete Idempotency-Key optionnel : un rejeu avec la meme cle et le meme "
                    + "corps ne declare jamais un second paiement pour cet ordre.")
    public ResponseEntity<ApiResponse<PaymentResponse>> submit(
            @PathVariable UUID orderId,
            @Valid @RequestBody SubmitPaymentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticatedUser CurrentUser currentUser) {
        // L'ordre fait partie de l'identite de l'endpoint (pas seulement du corps de la requete) :
        // la meme cle client, reutilisee par erreur sur un autre ordre, ne doit jamais renvoyer
        // la reponse d'un paiement different.
        String endpoint = "POST /api/v1/orders/" + orderId + "/payments";
        return idempotencyGuard.guard(currentUser.getId(), endpoint, idempotencyKey, request,
                new TypeReference<ApiResponse<PaymentResponse>>() {
                },
                () -> {
                    PaymentResponse response = paymentService.submit(orderId, request, currentUser.getId());
                    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response, "Paiement declare."));
                });
    }

    @PostMapping(value = "/payments/{paymentId}/proofs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Televerser une preuve de paiement",
            description = "Formats acceptes : JPEG, PNG, WEBP, PDF. Le type est verifie sur le "
                    + "contenu reel du fichier, pas seulement sur l'en-tete declare.")
    public ResponseEntity<ApiResponse<PaymentProofResponse>> uploadProof(
            @PathVariable UUID paymentId,
            @RequestParam("file") MultipartFile file,
            @AuthenticatedUser CurrentUser currentUser) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new StorageException("Lecture du fichier envoye impossible.", e);
        }
        PaymentProofResponse response = paymentService.uploadProof(paymentId, file.getOriginalFilename(),
                file.getContentType(), content, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response, "Preuve enregistree."));
    }

    @GetMapping("/payments/{paymentId}")
    @Operation(summary = "Detail d'un paiement")
    public ResponseEntity<ApiResponse<PaymentResponse>> get(
            @PathVariable UUID paymentId,
            @AuthenticatedUser CurrentUser currentUser) {
        return ResponseEntity.ok(ApiResponse.of(paymentService.get(paymentId, currentUser.getId())));
    }

    @GetMapping("/payments/{paymentId}/proofs/{proofId}")
    @Operation(summary = "Telecharger une preuve de paiement",
            description = "Reserve au proprietaire de l'ordre. Servi en piece jointe, jamais en ligne.")
    public ResponseEntity<Resource> downloadProof(
            @PathVariable UUID paymentId,
            @PathVariable UUID proofId,
            @AuthenticatedUser CurrentUser currentUser) {
        Resource resource = paymentService.downloadProof(paymentId, proofId, currentUser.getId());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
