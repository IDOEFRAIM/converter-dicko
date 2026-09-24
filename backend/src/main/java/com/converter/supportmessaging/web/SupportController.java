package com.converter.supportmessaging.web;

import com.converter.common.api.ApiResponse;
import com.converter.security.AuthenticatedUser;
import com.converter.security.CurrentUser;
import com.converter.supportmessaging.dto.SendSupportMessageRequest;
import com.converter.supportmessaging.dto.SupportMessageResponse;
import com.converter.supportmessaging.dto.SupportThreadResponse;
import com.converter.supportmessaging.service.SupportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Messagerie SAV du client : un seul fil continu, cree au premier message (mission client :
 * "les utilisateurs doivent pouvoir faire des reclamations").
 */
@RestController
@RequestMapping("/api/v1/support")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Messagerie SAV", description = "Fil de discussion continu entre le client et le support")
public class SupportController {

    private final SupportService supportService;

    public SupportController(SupportService supportService) {
        this.supportService = supportService;
    }

    @GetMapping("/thread")
    @Operation(summary = "Mon fil de messagerie SAV",
            description = "Cree automatiquement (vide) si l'utilisateur n'a encore jamais ecrit.")
    public ResponseEntity<ApiResponse<SupportThreadResponse>> myThread(@AuthenticatedUser CurrentUser currentUser) {
        return ResponseEntity.ok(ApiResponse.of(supportService.getOrCreateMyThread(currentUser.getId())));
    }

    @PostMapping("/messages")
    @Operation(summary = "Envoyer un message au support")
    public ResponseEntity<ApiResponse<SupportMessageResponse>> send(
            @Valid @RequestBody SendSupportMessageRequest request,
            @AuthenticatedUser CurrentUser currentUser) {
        SupportMessageResponse response = supportService.sendAsUser(currentUser.getId(), request.body());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response, "Message envoye."));
    }
}
