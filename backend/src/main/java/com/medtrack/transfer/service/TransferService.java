package com.medtrack.transfer.service;

import com.medtrack.inventory.dto.*;
import com.medtrack.inventory.service.FefoAllocationEngine;
import com.medtrack.inventory.service.InventoryMovementService;
import com.medtrack.medicine.entity.*;
import com.medtrack.medicine.repository.MedicineRepository;
import com.medtrack.shared.exception.*;
import com.medtrack.shared.idempotency.IdempotencyService;
import com.medtrack.shipment.entity.ShipmentStatus;
import com.medtrack.shipment.repository.ShipmentRepository;
import com.medtrack.transfer.dto.*;
import com.medtrack.transfer.entity.*;
import com.medtrack.transfer.repository.*;
import com.medtrack.user.entity.User;
import com.medtrack.user.repository.UserRepository;
import com.medtrack.warehouse.entity.*;
import com.medtrack.warehouse.repository.WarehouseRepository;
import jakarta.persistence.EntityManager;
import java.time.Year;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class TransferService {
    private final StockTransferRepository transfers;
    private final MedicineRepository medicines;
    private final WarehouseRepository warehouses;
    private final UserRepository users;
    private final FefoAllocationEngine fefo;
    private final InventoryMovementService inventory;
    private final ShipmentRepository shipments;
    private final IdempotencyService idempotency;
    private final EntityManager em;

    public TransferService(
        StockTransferRepository t,
        MedicineRepository m,
        WarehouseRepository w,
        UserRepository u,
        FefoAllocationEngine f,
        InventoryMovementService i,
        ShipmentRepository s,
        IdempotencyService idemp,
        EntityManager e
    ) {
        transfers = t;
        medicines = m;
        warehouses = w;
        users = u;
        fefo = f;
        inventory = i;
        shipments = s;
        idempotency = idemp;
        em = e;
    }

    @Transactional
    public TransferResponse create(UUID actorId, String idempotencyKey, TransferRequest r) {
        return idempotency.execute(actorId, idempotencyKey, "/api/v1/stock-transfers", r, TransferResponse.class, () -> {
            User actor = user(actorId);
            Warehouse source = actor.getAssignedWarehouse();
            if (source == null || source.getStatus() != WarehouseStatus.ACTIVE) {
                throw new DomainException("SOURCE_WAREHOUSE_REQUIRED", "An active assigned warehouse is required");
            }
            Warehouse dest = warehouse(r.destinationWarehouseId());
            if (source.getId().equals(dest.getId())) {
                throw new DomainException("TRANSFER_WAREHOUSE_MATCH", "Source and destination must differ");
            }
            String role = actor.getRole().getName();
            if (!"STORE_MANAGER".equals(role) && !"CENTRAL_WAREHOUSE_MANAGER".equals(role) && !"SUPER_ADMIN".equals(role)) {
                throw new ConflictException("TRANSFER_REQUEST_NOT_ALLOWED", "User cannot request transfers");
            }
            StockTransfer t = new StockTransfer(number(), source, dest, actor, r.notes());
            for (TransferRequest.Item item : r.items()) {
                Medicine m = medicines.findById(item.medicineId()).orElseThrow(() -> new NotFoundException("Medicine"));
                if (m.getStatus() != MedicineStatus.ACTIVE) {
                    throw new DomainException("MEDICINE_DISCONTINUED", "Discontinued medicine cannot be requested");
                }
                t.addItem(new StockTransferItem(m, item.quantity()));
            }
            return TransferResponse.of(transfers.save(t));
        });
    }

    @Transactional
    public TransferResponse approve(UUID actorId, UUID id) {
        StockTransfer t = lock(id);
        String role = user(actorId).getRole().getName();
        if (!"SUPER_ADMIN".equals(role) && !"CENTRAL_WAREHOUSE_MANAGER".equals(role)) {
            throw new ConflictException("TRANSFER_APPROVAL_NOT_ALLOWED", "User cannot approve transfers");
        }
        t.approve(user(actorId));
        return TransferResponse.of(t);
    }

    @Transactional
    public TransferResponse allocate(UUID actorId, UUID id, String idempotencyKey) {
        return idempotency.execute(actorId, idempotencyKey, "/api/v1/stock-transfers/" + id + "/allocate", null, TransferResponse.class, () -> {
            StockTransfer t = lock(id);
            if (t.getStatus() == TransferStatus.ALLOCATED) {
                return TransferResponse.of(t);
            }
            t.transition(TransferStatus.ALLOCATED);
            for (StockTransferItem requested : t.getItems().stream().filter(i -> i.getBatch() == null).toList()) {
                FefoAllocationResponse a = fefo.allocate(actorId, new FefoAllocationRequest(requested.getMedicine().getId(), t.getSourceWarehouse().getId(), requested.getRequestedQuantity()));
                for (FefoAllocationResponse.Allocation x : a.allocations()) {
                    com.medtrack.batch.entity.Batch b = em.getReference(com.medtrack.batch.entity.Batch.class, x.batchId());
                    t.addItem(new StockTransferItem(requested.getMedicine(), b, x.quantity()));
                }
            }
            return TransferResponse.of(t);
        });
    }

    @Transactional
    public TransferResponse pick(UUID actorId, UUID id, PickRequest r) {
        StockTransfer t = lock(id);
        t.transition(TransferStatus.PICKED);
        for (PickRequest.Item item : r.items()) {
            StockTransferItem match = t.getItems().stream().filter(i -> i.getBatch() != null && i.getBatch().getId().equals(item.batchId())).findFirst().orElseThrow(() -> new DomainException("UNALLOCATED_BATCH", "Picked batch was not allocated"));
            try {
                match.pick(item.quantity());
            } catch (IllegalArgumentException e) {
                throw new DomainException("INVALID_PICK_QUANTITY", e.getMessage());
            }
        }
        if (t.getItems().stream().filter(i -> i.getBatch() != null).anyMatch(i -> i.getPickedQuantity() != i.getAllocatedQuantity())) {
            throw new DomainException("INCOMPLETE_PICK", "All allocated batches must be picked");
        }
        return TransferResponse.of(t);
    }

    @Transactional
    public TransferResponse pack(UUID id) {
        StockTransfer t = lock(id);
        t.transition(TransferStatus.PACKED);
        return TransferResponse.of(t);
    }

    @Transactional
    public TransferResponse cancel(UUID actorId, UUID id, String reason, String idempotencyKey) {
        return idempotency.execute(actorId, idempotencyKey, "/api/v1/stock-transfers/" + id + "/cancel", reason != null ? Map.of("reason", reason) : null, TransferResponse.class, () -> {
            StockTransfer t = lock(id);
            User actor = user(actorId);
            String role = actor.getRole().getName();
            if (!"SUPER_ADMIN".equals(role) && !"CENTRAL_WAREHOUSE_MANAGER".equals(role) && !t.getRequestedBy().getId().equals(actor.getId())) {
                throw new ConflictException("TRANSFER_CANCEL_NOT_ALLOWED", "User cannot cancel this transfer");
            }
            TransferStatus currentStatus = t.getStatus();
            if (currentStatus == TransferStatus.DISPATCHED || currentStatus == TransferStatus.RECEIVED || currentStatus == TransferStatus.COMPLETED) {
                throw new ConflictException("CANNOT_CANCEL_DISPATCHED_TRANSFER", "Cannot cancel transfer in status " + currentStatus);
            }
            if (currentStatus == TransferStatus.CANCELLED || currentStatus == TransferStatus.REJECTED) {
                throw new ConflictException("TRANSFER_ALREADY_TERMINAL", "Transfer is already " + currentStatus);
            }

            // Release exact reserved stock back to available stock if allocated
            List<StockTransferItem> allocatedItems = t.getItems().stream().filter(i -> i.getBatch() != null && i.getAllocatedQuantity() > 0).toList();
            if (!allocatedItems.isEmpty()) {
                List<InventoryMovementService.Allocation> toRelease = new ArrayList<>();
                for (StockTransferItem item : allocatedItems) {
                    toRelease.add(new InventoryMovementService.Allocation(item.getBatch().getId(), item.getAllocatedQuantity(), t.getSourceWarehouse()));
                }
                inventory.releaseReservations(actor, t.getId(), t.getSourceWarehouse(), toRelease);
            }

            t.transition(TransferStatus.CANCELLED);

            // If a shipment exists in PREPARING status, cancel it as well
            shipments.findByTransfer_Id(t.getId()).ifPresent(s -> {
                if (s.getStatus() == ShipmentStatus.PREPARING) {
                    s.cancel();
                }
            });

            return TransferResponse.of(t);
        });
    }


    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<TransferResponse> list(org.springframework.data.domain.Pageable pageable) {
        return transfers.findAll(pageable).map(TransferResponse::of);
    }

    @Transactional(readOnly = true)
    public TransferResponse get(UUID id) {
        return TransferResponse.of(transfers.findById(id).orElseThrow(() -> new NotFoundException("Stock transfer")));
    }

    public StockTransfer locked(UUID id) {
        return lock(id);
    }

    private StockTransfer lock(UUID id) {
        return transfers.lockById(id).orElseThrow(() -> new NotFoundException("Stock transfer"));
    }

    private User user(UUID id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("User"));
    }

    private Warehouse warehouse(UUID id) {
        Warehouse w = warehouses.findById(id).orElseThrow(() -> new NotFoundException("Warehouse"));
        if (w.getStatus() != WarehouseStatus.ACTIVE) {
            throw new DomainException("WAREHOUSE_INACTIVE", "Warehouse is inactive");
        }
        return w;
    }

    private String number() {
        Number n = (Number) em.createNativeQuery("select nextval('medtrack_transfer_number_seq')").getSingleResult();
        return "TRF-" + Year.now().getValue() + "-%06d".formatted(n.longValue());
    }
}

