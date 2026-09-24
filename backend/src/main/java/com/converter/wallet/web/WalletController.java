package com.converter.wallet.web;

import com.converter.common.api.ApiResponse;
import com.converter.common.api.PageResponse;
import com.converter.security.AuthenticatedUser;
import com.converter.security.CurrentUser;
import com.converter.wallet.dto.WalletResponse;
import com.converter.wallet.dto.WalletTransactionResponse;
import com.converter.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consultation du Wallet client. Ownership implicite : le solde et
 * l'historique retournes sont toujours ceux de {@code currentUser},
 * jamais parametrables -- aucune fuite possible vers le wallet d'autrui.
 */
@RestController
@RequestMapping("/api/v1/wallet")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Wallet", description = "Solde interne XOF du client")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping
    @Operation(summary = "Mon solde Wallet", description = "Cree le wallet au premier acces si necessaire (solde a zero).")
    public ResponseEntity<ApiResponse<WalletResponse>> get(@AuthenticatedUser CurrentUser currentUser) {
        return ResponseEntity.ok(ApiResponse.of(walletService.snapshot(currentUser.getId())));
    }

    @GetMapping("/transactions")
    @Operation(summary = "Historique des mouvements de mon Wallet")
    public ResponseEntity<ApiResponse<PageResponse<WalletTransactionResponse>>> transactions(
            @AuthenticatedUser CurrentUser currentUser,
            @Parameter(hidden = true) @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.of(walletService.transactions(currentUser.getId(), pageable)));
    }
}
