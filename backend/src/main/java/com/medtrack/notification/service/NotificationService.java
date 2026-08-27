package com.medtrack.notification.service;

import com.medtrack.inventory.entity.InventoryBalance;
import com.medtrack.inventory.repository.InventoryBalanceRepository;
import com.medtrack.notification.entity.Notification;
import com.medtrack.notification.repository.NotificationRepository;
import com.medtrack.shared.exception.NotFoundException;
import com.medtrack.shipment.entity.Shipment;
import com.medtrack.shipment.entity.ShipmentStatus;
import com.medtrack.shipment.repository.ShipmentRepository;
import com.medtrack.user.entity.User;
import com.medtrack.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private final NotificationRepository notifications;
    private final InventoryBalanceRepository balances;
    private final ShipmentRepository shipments;
    private final UserRepository users;

    public NotificationService(
        NotificationRepository n,
        InventoryBalanceRepository b,
        ShipmentRepository s,
        UserRepository u
    ) {
        notifications = n;
        balances = b;
        shipments = s;
        users = u;
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void evaluate() {
        evaluateAlerts();
    }

    @Transactional
    public int evaluateAlerts() {
        int count = 0;
        Instant deduplicationWindow = Instant.now().minus(Duration.ofHours(24));
        LocalDate today = LocalDate.now();

        // 1. Expiry Alert Engine & Low Stock Engine
        for (InventoryBalance b : balances.findAll()) {
            if (b.getPhysicalQuantity() <= 0) continue;

            LocalDate expiry = b.getBatch().getExpiryDate();
            long daysToExpiry = ChronoUnit.DAYS.between(today, expiry);
            String expiryType = null;
            String title = null;
            String message = null;

            if (daysToExpiry <= 0) {
                expiryType = "EXPIRED";
                title = "Batch Expired: " + b.getBatch().getBatchNumber();
                message = "Batch " + b.getBatch().getBatchNumber() + " of " + b.getBatch().getMedicine().getGenericName()
                        + " has expired on " + expiry + ". Immediate quarantine required.";
            } else if (daysToExpiry <= 30) {
                expiryType = "EXPIRY_CRITICAL";
                String uom = b.getBatch().getMedicine().getUnitOfMeasure() != null ? b.getBatch().getMedicine().getUnitOfMeasure() : "units";
                title = "Critical Expiry Alert (≤30 Days): " + b.getBatch().getBatchNumber();
                message = "Batch " + b.getBatch().getBatchNumber() + " (" + b.getBatch().getMedicine().getGenericName()
                        + ") expires in " + daysToExpiry + " days (Expiry: " + expiry + "). Available: " + b.getAvailableQuantity() + " " + uom;
            } else if (daysToExpiry <= 90) {
                expiryType = "NEAR_EXPIRY";
                String uom = b.getBatch().getMedicine().getUnitOfMeasure() != null ? b.getBatch().getMedicine().getUnitOfMeasure() : "units";
                title = "Near Expiry Alert (≤90 Days): " + b.getBatch().getBatchNumber();
                message = "Batch " + b.getBatch().getBatchNumber() + " (" + b.getBatch().getMedicine().getGenericName()
                        + ") expires in " + daysToExpiry + " days (Expiry: " + expiry + "). Available: " + b.getAvailableQuantity() + " " + uom;
            }

            if (expiryType != null) {
                boolean alreadyNotified = notifications.existsByTypeAndEntityTypeAndEntityIdAndCreatedAtAfter(
                        expiryType, "BATCH", b.getBatch().getId(), deduplicationWindow
                );
                if (!alreadyNotified) {
                    notifications.save(new Notification(b.getWarehouse(), expiryType, title, message, "BATCH", b.getBatch().getId()));
                    count++;
                }
            }

            // Low stock check
            if (b.getAvailableQuantity() <= b.getBatch().getMedicine().getMinStockThreshold()) {
                boolean alreadyLowStock = notifications.existsByTypeAndEntityTypeAndEntityIdAndCreatedAtAfter(
                        "LOW_STOCK", "BATCH", b.getBatch().getId(), deduplicationWindow
                );
                if (!alreadyLowStock) {
                    notifications.save(new Notification(
                            b.getWarehouse(),
                            "LOW_STOCK",
                            "Low Stock Alert: " + b.getBatch().getMedicine().getGenericName(),
                            "Available stock (" + b.getAvailableQuantity() + ") is at or below threshold ("
                                    + b.getBatch().getMedicine().getMinStockThreshold() + ") in " + b.getWarehouse().getName(),
                            "BATCH",
                            b.getBatch().getId()
                    ));
                    count++;
                }
            }
        }

        // 2. Shipment-Delay Alert Engine
        Instant now = Instant.now();
        for (Shipment s : shipments.findAll()) {
            if (s.getStatus() == ShipmentStatus.DELIVERED || s.getStatus() == ShipmentStatus.CANCELLED) {
                continue;
            }

            if (s.getEstimatedArrival().isBefore(now)) {
                boolean alreadyNotified = notifications.existsByTypeAndEntityTypeAndEntityIdAndCreatedAtAfter(
                        "SHIPMENT_DELAY", "SHIPMENT", s.getId(), deduplicationWindow
                );
                if (!alreadyNotified) {
                    notifications.save(new Notification(
                            s.getDestination(),
                            "SHIPMENT_DELAY",
                            "Shipment Delay Alert: " + s.getShipmentNumber(),
                            "Shipment " + s.getShipmentNumber() + " (Carrier: " + s.getCarrierName() + ", Tracking: "
                                    + s.getTrackingNumber() + ") to " + s.getDestination().getName()
                                    + " has exceeded its estimated arrival time (" + s.getEstimatedArrival() + ").",
                            "SHIPMENT",
                            s.getId()
                    ));
                    count++;
                }
            }
        }

        return count;
    }

    @Transactional(readOnly = true)
    public List<Notification> getUnreadForUser(UUID userId) {
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("User"));
        UUID warehouseId = user.getAssignedWarehouse() != null ? user.getAssignedWarehouse().getId() : null;
        return notifications.findUnreadForUserOrWarehouse(userId, warehouseId);
    }

    @Transactional(readOnly = true)
    public List<Notification> getAllUnread() {
        return notifications.findByReadAtIsNullOrderByCreatedAtDesc();
    }

    @Transactional
    public void markRead(UUID notificationId) {
        Notification n = notifications.findById(notificationId).orElseThrow(() -> new NotFoundException("Notification"));
        n.markRead();
    }
}