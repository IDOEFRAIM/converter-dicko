package com.converter.notification.web;

import com.converter.common.api.ApiResponse;
import com.converter.common.api.PageResponse;
import com.converter.notification.dto.NotificationResponse;
import com.converter.notification.dto.UnreadCountResponse;
import com.converter.notification.service.NotificationService;
import com.converter.security.AuthenticatedUser;
import com.converter.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Notifications internes du client. Ownership : 404, jamais 403, comme partout ailleurs. */
@RestController
@RequestMapping("/api/v1/notifications")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Notifications", description = "Notifications internes du client")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "Mes notifications, les plus recentes en premier")
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> list(
            @AuthenticatedUser CurrentUser currentUser,
            @Parameter(hidden = true) @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.of(notificationService.listMine(currentUser.getId(), pageable)));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Nombre de notifications non lues")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> unreadCount(@AuthenticatedUser CurrentUser currentUser) {
        return ResponseEntity.ok(ApiResponse.of(new UnreadCountResponse(notificationService.unreadCount(currentUser.getId()))));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Marquer une notification comme lue")
    public ResponseEntity<ApiResponse<NotificationResponse>> markRead(
            @PathVariable UUID id,
            @AuthenticatedUser CurrentUser currentUser) {
        return ResponseEntity.ok(ApiResponse.of(notificationService.markRead(id, currentUser.getId()), "Notification marquee comme lue."));
    }
}
