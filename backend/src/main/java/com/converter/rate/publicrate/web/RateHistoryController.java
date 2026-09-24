package com.converter.rate.publicrate.web;

import com.converter.common.api.ApiResponse;
import com.converter.common.api.PageResponse;
import com.converter.rate.provider.RateProvider;
import com.converter.rate.publicrate.dto.PublicRateHistoryEntry;
import com.converter.rate.publicrate.service.PublicRateSnapshotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Historique public du taux client — accessible a tout utilisateur authentifie (pas seulement
 * l'administration), sous {@code /api/v1/**} comme le reste des endpoints client (quotes,
 * ordres, fournisseurs). "Public" signifie ici <b>jamais admin-only</b>, pas
 * <b>anonyme/non-authentifie</b> : aucun endpoint de taux existant (voir
 * {@code AdminRateController}/{@code AdminCostRateController}, tous deux {@code /api/admin/**})
 * n'a jamais ete accessible sans jeton, et seul {@code /api/settings/public} l'est reellement
 * dans ce backend — un choix delibere pour ce endpoint-ci, pas une supposition.
 *
 * <p>Ne renvoie jamais que {@link PublicRateHistoryEntry} — voir {@code
 * PublicRateSnapshotService} pour la frontiere de confidentialite qui garantit qu'aucune donnee
 * de {@code daily_cost_rate_configurations} ne peut fuiter par ce chemin.
 */
@RestController
@RequestMapping("/api/v1/rates")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Taux", description = "Historique public du taux client XOF/CNY")
public class RateHistoryController {

    private final PublicRateSnapshotService publicRateSnapshotService;

    public RateHistoryController(PublicRateSnapshotService publicRateSnapshotService) {
        this.publicRateSnapshotService = publicRateSnapshotService;
    }

    @GetMapping("/history")
    @Operation(summary = "Historique du taux client",
            description = "Convention 1 CNY = customerRate XOF, identique aux devis. Trie par recordedAt "
                    + "decroissant (le plus recent en premier), id decroissant en cas d'egalite — tri fixe, "
                    + "non modifiable par le client. Uniquement le taux commercial : jamais le taux de "
                    + "revient, la marge ou les frais internes.")
    public ResponseEntity<ApiResponse<PageResponse<PublicRateHistoryEntry>>> history(
            @RequestParam(required = false, defaultValue = RateProvider.DEFAULT_CURRENCY_PAIR) String pair,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @Parameter(hidden = true) @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.of(publicRateSnapshotService.history(pair, from, to, pageable)));
    }
}
