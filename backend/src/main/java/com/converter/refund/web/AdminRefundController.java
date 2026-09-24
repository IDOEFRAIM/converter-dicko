package com.converter.refund.web;

import com.converter.common.api.ApiResponse;
import com.converter.common.idempotency.IdempotencyGuard;
import com.converter.refund.dto.CreateRefundRequest;
import com.converter.refund.dto.ProcessRefundRequest;
import com.converter.refund.dto.RefundResponse;
import com.converter.refund.dto.RejectRefundRequest;
import com.converter.refund.service.RefundService;
import com.converter.security.AuthenticatedUser;
import com.converter.security.CurrentUser;
import com.fasterxml.jackson.core.type.TypeReference;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Remboursement XOF au client, reserve a l'administration — jamais declenchable par le client
 * lui-meme (voir {@code RefundService} pour le raisonnement complet). Independant du
 * {@code Settlement} : rembourser un client ne modifie jamais {@code Order.status}, et
 * {@code SettlementService#execute} refuse a son tour tout decaissement CNY des qu'un
 * remboursement a ete effectivement traite pour l'ordre concerne.
 */
@RestController
@RequestMapping("/api/admin")
@SecurityRequirement(name = "bearer-jwt")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administration - Remboursements", description = "Remboursement XOF manuel au client")
public class AdminRefundController {

    private final RefundService refundService;
    private final IdempotencyGuard idempotencyGuard;

    public AdminRefundController(RefundService refundService, IdempotencyGuard idempotencyGuard) {
        this.refundService = refundService;
        this.idempotencyGuard = idempotencyGuard;
    }

    @PostMapping("/payments/{paymentId}/refunds")
    @Operation(summary = "Creer un remboursement",
            description = "Requiert un paiement CONFIRMED. Le montant est toujours exactement celui "
                    + "recu par le client, jamais saisi librement. Ne modifie ni l'Order ni le Settlement "
                    + "— statut PENDING, aucun decaissement tant que /process n'a pas ete appele. "
                    + "En-tete Idempotency-Key optionnel : un rejeu avec la meme cle et le meme corps "
                    + "ne cree jamais un second remboursement.")
    public ResponseEntity<ApiResponse<RefundResponse>> create(
            @PathVariable UUID paymentId,
            @Valid @RequestBody CreateRefundRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticatedUser CurrentUser actor) {
        String endpoint = "POST /api/admin/payments/" + paymentId + "/refunds";
        return idempotencyGuard.guard(actor.getId(), endpoint, idempotencyKey, request,
                new TypeReference<ApiResponse<RefundResponse>>() {
                },
                () -> {
                    RefundResponse response = refundService.create(paymentId, request.reason(), actor.getId());
                    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response, "Remboursement cree."));
                });
    }

    @PostMapping("/refunds/{id}/process")
    @Operation(summary = "Enregistrer le decaissement XOF reel",
            description = "Reference de transaction obligatoire. Decaisse le solde XOF de la tresorerie "
                    + "(hors reservation, jamais lie au CNY). En-tete Idempotency-Key optionnel : un rejeu "
                    + "avec la meme cle et le meme corps ne decaisse jamais deux fois.")
    public ResponseEntity<ApiResponse<RefundResponse>> process(
            @PathVariable UUID id,
            @Valid @RequestBody ProcessRefundRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticatedUser CurrentUser actor) {
        String endpoint = "POST /api/admin/refunds/" + id + "/process";
        return idempotencyGuard.guard(actor.getId(), endpoint, idempotencyKey, request,
                new TypeReference<ApiResponse<RefundResponse>>() {
                },
                () -> {
                    RefundResponse response = refundService.process(id, request.transactionReference(), actor.getId());
                    return ResponseEntity.ok(ApiResponse.of(response, "Remboursement traite."));
                });
    }

    @PostMapping("/refunds/{id}/reject")
    @Operation(summary = "Rejeter un remboursement en attente")
    public ResponseEntity<ApiResponse<RefundResponse>> reject(
            @PathVariable UUID id,
            @Valid @RequestBody RejectRefundRequest request,
            @AuthenticatedUser CurrentUser actor) {
        RefundResponse response = refundService.reject(id, request.reason(), actor.getId());
        return ResponseEntity.ok(ApiResponse.of(response, "Remboursement rejete."));
    }

    @GetMapping("/refunds/{id}")
    @Operation(summary = "Detail d'un remboursement")
    public ResponseEntity<ApiResponse<RefundResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(refundService.get(id)));
    }

    @GetMapping("/payments/{paymentId}/refund")
    @Operation(summary = "Remboursement associe a un paiement, s'il existe")
    public ResponseEntity<ApiResponse<RefundResponse>> getByPayment(@PathVariable UUID paymentId) {
        return ResponseEntity.ok(ApiResponse.of(refundService.getByPayment(paymentId)));
    }
}
