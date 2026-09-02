package com.converter.treasury.web;

import com.converter.common.api.ApiResponse;
import com.converter.common.api.PageResponse;
import com.converter.common.idempotency.IdempotencyGuard;
import com.converter.security.AuthenticatedUser;
import com.converter.security.CurrentUser;
import com.converter.treasury.domain.Currency;
import com.converter.treasury.domain.TreasuryTransaction;
import com.converter.treasury.dto.TreasuryAccountResponse;
import com.converter.treasury.dto.TreasuryAdjustmentRequest;
import com.converter.treasury.dto.TreasuryTransactionResponse;
import com.converter.treasury.repository.TreasuryTransactionRepository;
import com.converter.treasury.service.TreasuryService;
import com.fasterxml.jackson.core.type.TypeReference;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * Consultation et alimentation manuelle de la tresorerie, reservees a
 * l'administration. Les mouvements lies aux ordres (reservation,
 * liberation, consommation) ne transitent jamais par ces endpoints :
 * ils sont declenches automatiquement par {@code order}/{@code settlement}.
 *
 * <p>{@code deposit}/{@code adjust} sont les deux seuls endpoints financiers de cette API
 * <b>sans aucun filet SQL</b> contre un rejeu (aucune contrainte d'unicite n'a de sens sur un
 * mouvement manuel libre) : l'en-tete {@code Idempotency-Key} y est donc la seule protection
 * contre un double credit/ajustement reel.
 */
@RestController
@RequestMapping("/api/admin/treasury")
@SecurityRequirement(name = "bearer-jwt")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administration - Tresorerie", description = "Soldes, ledger et alimentation manuelle")
public class AdminTreasuryController {

    private final TreasuryService treasuryService;
    private final TreasuryTransactionRepository transactionRepository;
    private final IdempotencyGuard idempotencyGuard;

    public AdminTreasuryController(TreasuryService treasuryService,
                                   TreasuryTransactionRepository transactionRepository,
                                   IdempotencyGuard idempotencyGuard) {
        this.treasuryService = treasuryService;
        this.transactionRepository = transactionRepository;
        this.idempotencyGuard = idempotencyGuard;
    }

    @GetMapping("/accounts/{currency}")
    @Operation(summary = "Solde d'un compte de tresorerie")
    public ResponseEntity<ApiResponse<TreasuryAccountResponse>> account(@PathVariable Currency currency) {
        return ResponseEntity.ok(ApiResponse.of(treasuryService.snapshot(currency)));
    }

    @GetMapping("/accounts/{currency}/transactions")
    @Operation(summary = "Ledger paginé d'un compte", description = "Le plus recent en premier.")
    public ResponseEntity<ApiResponse<PageResponse<TreasuryTransactionResponse>>> transactions(
            @PathVariable Currency currency,
            @Parameter(hidden = true) @PageableDefault(size = 30) Pageable pageable) {
        TreasuryAccountResponse account = treasuryService.snapshot(currency);
        Page<TreasuryTransactionResponse> page = transactionRepository
                .findByAccountIdOrderByCreatedAtDesc(account.id(), pageable)
                .map(AdminTreasuryController::toResponse);
        return ResponseEntity.ok(ApiResponse.of(PageResponse.from(page)));
    }

    @PostMapping("/deposit")
    @Operation(summary = "Alimentation manuelle",
            description = "Ajoute de la liquidite disponible, hors reservation. En-tete "
                    + "Idempotency-Key fortement recommande : sans contrainte SQL d'unicite sur un "
                    + "mouvement manuel, c'est l'unique protection contre un double credit reel.")
    public ResponseEntity<ApiResponse<TreasuryAccountResponse>> deposit(
            @Valid @RequestBody TreasuryAdjustmentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticatedUser CurrentUser actor) {
        return idempotencyGuard.guard(actor.getId(), "POST /api/admin/treasury/deposit", idempotencyKey, request,
                new TypeReference<ApiResponse<TreasuryAccountResponse>>() {
                },
                () -> {
                    BigDecimal amount = request.amount().abs();
                    TreasuryAccountResponse result = treasuryService.deposit(request.currency(), amount,
                            actor.getId(), request.reason());
                    return ResponseEntity.ok(ApiResponse.of(result, "Depot enregistre."));
                });
    }

    @PostMapping("/adjust")
    @Operation(summary = "Correction comptable",
            description = "Montant positif pour ajouter, negatif pour retirer. Motif obligatoire. "
                    + "En-tete Idempotency-Key fortement recommande, meme raison que /deposit.")
    public ResponseEntity<ApiResponse<TreasuryAccountResponse>> adjust(
            @Valid @RequestBody TreasuryAdjustmentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticatedUser CurrentUser actor) {
        return idempotencyGuard.guard(actor.getId(), "POST /api/admin/treasury/adjust", idempotencyKey, request,
                new TypeReference<ApiResponse<TreasuryAccountResponse>>() {
                },
                () -> {
                    TreasuryAccountResponse result = treasuryService.adjust(request.currency(), request.amount(),
                            actor.getId(), request.reason());
                    return ResponseEntity.ok(ApiResponse.of(result, "Ajustement enregistre."));
                });
    }

    private static TreasuryTransactionResponse toResponse(TreasuryTransaction tx) {
        return new TreasuryTransactionResponse(
                tx.getId(), tx.getAccountId(), tx.getType(), tx.getAmount(), tx.getBalanceAfter(),
                tx.getReservedAfter(), tx.getOrderId(), tx.getPerformedBy(), tx.getReason(), tx.getCreatedAt());
    }
}
