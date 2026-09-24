package com.converter.business.reporting.web;

import com.converter.business.reporting.dto.BusinessPaymentSummaryResponse;
import com.converter.business.reporting.service.BusinessPaymentReportService;
import com.converter.common.api.ApiResponse;
import com.converter.security.AuthenticatedUser;
import com.converter.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Reporting consolide reserve aux utilisateurs disposant d'un {@code BusinessProfile} — voir
 * {@code BusinessPaymentReportService} pour la regle exacte (404, jamais 403, si le profil est
 * absent).
 */
@RestController
@RequestMapping("/api/v1/business")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Reporting professionnel", description = "Vue consolidee des ordres pour les profils Business")
public class BusinessReportingController {

    private final BusinessPaymentReportService businessPaymentReportService;

    public BusinessReportingController(BusinessPaymentReportService businessPaymentReportService) {
        this.businessPaymentReportService = businessPaymentReportService;
    }

    @GetMapping("/payments/summary")
    @Operation(summary = "Resume consolide des ordres",
            description = "Reserve aux profils Business (404 BUSINESS_PROFILE_NOT_FOUND sinon). Montants agreges "
                    + "uniquement a partir des ordres COMPLETED -- jamais un recalcul de taux/frais. "
                    + "from/to optionnels, convention from <= createdAt < to.")
    public ResponseEntity<ApiResponse<BusinessPaymentSummaryResponse>> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @AuthenticatedUser CurrentUser currentUser) {
        return ResponseEntity.ok(ApiResponse.of(businessPaymentReportService.summary(currentUser.getId(), from, to)));
    }
}
