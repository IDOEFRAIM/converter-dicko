package com.converter.notification.dto;

import com.converter.notification.domain.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        String title,
        String message,
        Instant createdAt,
        Instant readAt
) {
}
