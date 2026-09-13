package com.converter.supportmessaging.web;

import com.converter.common.api.ApiResponse;
import com.converter.common.api.PageResponse;
import com.converter.security.AuthenticatedUser;
import com.converter.security.CurrentUser;
import com.converter.supportmessaging.dto.SendSupportMessageRequest;
import com.converter.supportmessaging.dto.SupportMessageResponse;
import com.converter.supportmessaging.dto.SupportThreadResponse;
import com.converter.supportmessaging.dto.SupportThreadSummaryResponse;
import com.converter.supportmessaging.service.SupportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Boite de reception SAV admin : tous les fils, un par utilisateur, n'importe quel administrateur
 * peut repondre dans n'importe lequel.
 */
@RestController
@RequestMapping("/api/admin/support")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Messagerie SAV (admin)", description = "Boite de reception des reclamations utilisateur")
public class AdminSupportController {

    private final SupportService supportService;

    public AdminSupportController(SupportService supportService) {
        this.supportService = supportService;
    }

    @GetMapping("/threads")
    @Operation(summary = "Lister tous les fils de messagerie", description = "Tries par activite la plus recente.")
    public ResponseEntity<ApiResponse<PageResponse<SupportThreadSummaryResponse>>> listThreads(
            @Parameter(hidden = true) @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.of(supportService.listThreadsForAdmin(pageable)));
    }

    @GetMapping("/threads/{userId}/messages")
    @Operation(summary = "Consulter le fil d'un utilisateur")
    public ResponseEntity<ApiResponse<SupportThreadResponse>> getThread(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.of(supportService.getThreadForAdmin(userId)));
    }

    @PostMapping("/threads/{userId}/messages")
    @Operation(summary = "Repondre dans le fil d'un utilisateur")
    public ResponseEntity<ApiResponse<SupportMessageResponse>> reply(
            @PathVariable UUID userId,
            @Valid @RequestBody SendSupportMessageRequest request,
            @AuthenticatedUser CurrentUser currentUser) {
        SupportMessageResponse response = supportService.sendAsAdmin(userId, request.body(), currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response, "Reponse envoyee."));
    }
}
