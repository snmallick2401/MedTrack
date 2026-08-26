package com.medtrack.transfer.controller;

import com.medtrack.transfer.dto.*;
import com.medtrack.transfer.service.TransferService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/stock-transfers")
public class TransferController {
    private final TransferService service;

    public TransferController(TransferService s) {
        service = s;
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('STORE_MANAGER') or hasRole('CENTRAL_WAREHOUSE_MANAGER')")
    public ResponseEntity<TransferResponse> create(
        @AuthenticationPrincipal String user,
        @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
        @Valid @RequestBody TransferRequest r
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(UUID.fromString(user), idempotencyKey, r));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('CENTRAL_WAREHOUSE_MANAGER')")
    public TransferResponse approve(@AuthenticationPrincipal String user, @PathVariable UUID id) {
        return service.approve(UUID.fromString(user), id);
    }

    @PostMapping("/{id}/allocate")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('CENTRAL_WAREHOUSE_MANAGER')")
    public TransferResponse allocate(
        @AuthenticationPrincipal String user,
        @PathVariable UUID id,
        @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey
    ) {
        return service.allocate(UUID.fromString(user), id, idempotencyKey);
    }

    @PostMapping("/{id}/pick")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('CENTRAL_WAREHOUSE_MANAGER')")
    public TransferResponse pick(@AuthenticationPrincipal String user, @PathVariable UUID id, @Valid @RequestBody PickRequest r) {
        return service.pick(UUID.fromString(user), id, r);
    }

    @PostMapping("/{id}/pack")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('CENTRAL_WAREHOUSE_MANAGER')")
    public TransferResponse pack(@PathVariable UUID id) {
        return service.pack(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('CENTRAL_WAREHOUSE_MANAGER') or hasRole('STORE_MANAGER')")
    public TransferResponse cancel(
        @AuthenticationPrincipal String user,
        @PathVariable UUID id,
        @RequestBody(required = false) Map<String, String> body,
        @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey
    ) {
        String reason = body != null ? body.get("reason") : null;
        return service.cancel(UUID.fromString(user), id, reason, idempotencyKey);
    }
}

