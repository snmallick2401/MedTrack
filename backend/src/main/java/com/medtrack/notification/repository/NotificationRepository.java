package com.medtrack.notification.repository;

import com.medtrack.notification.entity.Notification;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByReadAtIsNullOrderByCreatedAtDesc();

    @Query("SELECT n FROM Notification n WHERE (n.userId = :userId OR (n.warehouse.id = :warehouseId AND n.userId IS NULL) OR (n.userId IS NULL AND n.warehouse IS NULL)) AND n.readAt IS NULL ORDER BY n.createdAt DESC")
    List<Notification> findUnreadForUserOrWarehouse(@Param("userId") UUID userId, @Param("warehouseId") UUID warehouseId);

    boolean existsByTypeAndEntityTypeAndEntityIdAndReadAtIsNull(String type, String entityType, UUID entityId);

    boolean existsByTypeAndEntityTypeAndEntityIdAndCreatedAtAfter(String type, String entityType, UUID entityId, Instant after);
}