package com.converter.kyc.web;

import com.converter.common.api.ApiResponse;
import com.converter.common.exception.BusinessException;
import com.converter.common.exception.ErrorCode;
import com.converter.kyc.domain.KycDocumentType;
import com.converter.kyc.dto.KycFileUpload;
import com.converter.kyc.dto.KycSubmissionResponse;
import com.converter.kyc.service.KycService;
import com.converter.security.AuthenticatedUser;
import com.converter.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Parcours utilisateur de verification d'identite (remarque produit #6) : soumettre un dossier,
 * consulter son statut. La revue est faite par un administrateur ({@code AdminKycController}).
 */
@RestController
@RequestMapping("/api/v1/kyc")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Verification d'identite", description = "Soumission et suivi du dossier KYC")
public class KycController {

    private final KycService kycService;

    public KycController(KycService kycService) {
        this.kycService = kycService;
    }

    @PostMapping(value = "/submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Soumettre un dossier de verification d'identite",
            description = "Multipart : documentType + recto (front) + verso (back, requis sauf passeport) + selfie. "
                    + "Un seul dossier en attente a la fois. Refuse si l'identite est deja verifiee.")
    public ResponseEntity<ApiResponse<KycSubmissionResponse>> submit(
            @RequestParam("documentType") KycDocumentType documentType,
            @RequestParam("front") MultipartFile front,
            @RequestParam(value = "back", required = false) MultipartFile back,
            @RequestParam("selfie") MultipartFile selfie,
            @AuthenticatedUser CurrentUser currentUser) {
        KycSubmissionResponse response = kycService.submit(currentUser.getId(), documentType,
                toUpload(front), toUpload(back), toUpload(selfie));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response, "Dossier soumis."));
    }

    @GetMapping("/submissions/me")
    @Operation(summary = "Mon dernier dossier KYC",
            description = "Renvoie le statut du dernier dossier (PENDING / APPROVED / REJECTED) ou "
                    + "{@code data: null} si aucun n'a jamais ete soumis.")
    public ResponseEntity<ApiResponse<KycSubmissionResponse>> mine(@AuthenticatedUser CurrentUser currentUser) {
        return ResponseEntity.ok(ApiResponse.of(kycService.mySubmission(currentUser.getId()).orElse(null)));
    }

    private static KycFileUpload toUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        try {
            return new KycFileUpload(file.getOriginalFilename(), file.getContentType(), file.getBytes());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Fichier illisible : " + file.getOriginalFilename());
        }
    }
}
