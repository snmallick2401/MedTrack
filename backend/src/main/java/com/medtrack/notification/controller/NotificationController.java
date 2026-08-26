package com.medtrack.notification.controller;

import com.medtrack.notification.dto.NotificationResponse;
import com.medtrack.notification.service.NotificationService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService s) {
        this.service = s;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<NotificationResponse> list(@AuthenticationPrincipal String user) {
        return service.getUnreadForUser(UUID.fromString(user)).stream()
                .map(NotificationResponse::of)
                .toList();
    }

    @PostMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markRead(@PathVariable UUID id) {
        service.markRead(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/evaluate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Map<String, Object> evaluate() {
        int count = service.evaluateAlerts();
        return Map.of("newNotificationsCreated", count);
    }
}