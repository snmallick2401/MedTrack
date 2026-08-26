package com.medtrack.shipment.controller;

import com.medtrack.shipment.dto.*;
import com.medtrack.shipment.service.ShipmentService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ShipmentController {
    private final ShipmentService service;

    public ShipmentController(ShipmentService s) {
        service = s;
    }

    @PostMapping("/shipments")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('LOGISTICS_COORDINATOR')")
    public ResponseEntity<ShipmentResponse> create(@Valid @RequestBody ShipmentRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @PostMapping("/stock-transfers/{id}/dispatch")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('CENTRAL_WAREHOUSE_MANAGER') or hasRole('LOGISTICS_COORDINATOR')")
    public ShipmentResponse dispatch(
        @AuthenticationPrincipal String user,
        @PathVariable UUID id,
        @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey
    ) {
        return service.dispatch(UUID.fromString(user), id, idempotencyKey);
    }

    @PostMapping("/stock-transfers/{id}/receive")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('STORE_MANAGER')")
    public Map<String, String> receive(
        @AuthenticationPrincipal String user,
        @PathVariable UUID id,
        @Valid @RequestBody ReceiveRequest r,
        @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey
    ) {
        return service.receive(UUID.fromString(user), id, r, idempotencyKey);
    }
}

