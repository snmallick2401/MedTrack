package com.medtrack.inventory.controller;

import com.medtrack.inventory.dto.*;
import com.medtrack.inventory.repository.*;
import com.medtrack.inventory.service.*;
import com.medtrack.shared.exception.NotFoundException;
import com.medtrack.user.entity.User;
import com.medtrack.user.repository.UserRepository;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InboundService inbound;
    private final FefoAllocationEngine fefo;
    private final InventoryBalanceRepository balances;
    private final InventoryJournalEntryRepository journals;
    private final InventoryAuthorizationService authorizationService;
    private final UserRepository users;

    public InventoryController(
        InboundService i,
        FefoAllocationEngine f,
        InventoryBalanceRepository b,
        InventoryJournalEntryRepository j,
        InventoryAuthorizationService auth,
        UserRepository u
    ) {
        this.inbound = i;
        this.fefo = f;
        this.balances = b;
        this.journals = j;
        this.authorizationService = auth;
        this.users = u;
    }

    @PostMapping("/inbound")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('CENTRAL_WAREHOUSE_MANAGER')")
    public ResponseEntity<InboundReceiptResponse> inbound(
        @AuthenticationPrincipal String actorId,
        @RequestHeader(name = "X-Idempotency-Key", required = false) String key,
        @Valid @RequestBody InboundReceiptRequest request
    ) {
        InboundReceiptResponse response = inbound.receive(UUID.fromString(actorId), key, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/balances")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('CENTRAL_WAREHOUSE_MANAGER') or hasRole('STORE_MANAGER') or hasRole('AUDITOR')")
    public Page<InventoryBalanceResponse> balances(
        @AuthenticationPrincipal String actorId,
        @RequestParam(required = false) UUID warehouseId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        User actor = users.findById(UUID.fromString(actorId))
                .orElseThrow(() -> new NotFoundException("User"));
        UUID authorizedWarehouseId = authorizationService.resolveAuthorizedWarehouse(actor, warehouseId);
        Pageable pageable = PageRequest.of(page, Math.min(Math.max(size, 1), 100));

        if (authorizedWarehouseId != null) {
            return balances.findByWarehouse_Id(authorizedWarehouseId, pageable).map(InventoryBalanceResponse::of);
        } else {
            return balances.findAllWithDetails(pageable).map(InventoryBalanceResponse::of);
        }
    }

    @GetMapping("/journal-entries")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('AUDITOR')")
    public Page<JournalEntryResponse> journals(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return journals.findAll(PageRequest.of(page, Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(JournalEntryResponse::of);
    }

    @GetMapping("/journal-entries/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('AUDITOR')")
    public JournalEntryDetailResponse journal(@PathVariable UUID id) {
        return JournalEntryDetailResponse.of(journals.findWithLinesById(id)
                .orElseThrow(() -> new NotFoundException("Inventory journal entry")));
    }

    @PostMapping("/allocations/fefo")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('CENTRAL_WAREHOUSE_MANAGER')")
    public FefoAllocationResponse allocate(
        @AuthenticationPrincipal String actorId,
        @Valid @RequestBody FefoAllocationRequest request
    ) {
        return fefo.allocate(UUID.fromString(actorId), request);
    }

    @PostMapping("/allocations/manual")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('CENTRAL_WAREHOUSE_MANAGER')")
    public FefoAllocationResponse override(
        @AuthenticationPrincipal String actorId,
        @Valid @RequestBody FefoOverrideRequest request
    ) {
        return fefo.override(UUID.fromString(actorId), request);
    }
}
