package com.medtrack.notification.dto;

import com.medtrack.notification.entity.Notification;
import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
    UUID id,
    String type,
    String title,
    String message,
    String entityType,
    UUID entityId,
    UUID warehouseId,
    Instant createdAt,
    Instant readAt
) {
    public static NotificationResponse of(Notification n) {
        return new NotificationResponse(
            n.getId(),
            n.getType(),
            n.getTitle(),
            n.getMessage(),
            n.getEntityType(),
            n.getEntityId(),
            n.getWarehouse() != null ? n.getWarehouse().getId() : null,
            n.getCreatedAt(),
            n.getReadAt()
        );
    }
}