package com.medtrack.notification.entity;

import com.medtrack.shared.model.BaseEntity;
import com.medtrack.warehouse.entity.Warehouse;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification extends BaseEntity {
    @Column(name = "user_id")
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String message;

    @Column(name = "entity_type")
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {}

    public Notification(Warehouse w, String type, String title, String message, String entityType, UUID entityId) {
        this.warehouse = w;
        this.type = type;
        this.title = title;
        this.message = message;
        this.entityType = entityType;
        this.entityId = entityId;
    }

    public Notification(UUID userId, Warehouse w, String type, String title, String message, String entityType, UUID entityId) {
        this.userId = userId;
        this.warehouse = w;
        this.type = type;
        this.title = title;
        this.message = message;
        this.entityType = entityType;
        this.entityId = entityId;
    }

    public void markRead() {
        this.readAt = Instant.now();
    }

    public UUID getUserId() { return userId; }
    public Warehouse getWarehouse() { return warehouse; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getEntityType() { return entityType; }
    public UUID getEntityId() { return entityId; }
    public Instant getReadAt() { return readAt; }
}