package com.medtrack.shipment.service;

import com.medtrack.inventory.service.InventoryMovementService;
import com.medtrack.shared.exception.*;
import com.medtrack.shared.idempotency.IdempotencyService;
import com.medtrack.shipment.dto.*;
import com.medtrack.shipment.entity.*;
import com.medtrack.shipment.repository.ShipmentRepository;
import com.medtrack.transfer.entity.*;
import com.medtrack.transfer.repository.StockTransferRepository;
import com.medtrack.user.entity.User;
import com.medtrack.user.repository.UserRepository;
import com.medtrack.warehouse.entity.StorageLocation;
import com.medtrack.warehouse.repository.StorageLocationRepository;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class ShipmentService {
    private final ShipmentRepository shipments;
    private final StockTransferRepository transfers;
    private final UserRepository users;
    private final StorageLocationRepository locations;
    private final InventoryMovementService inventory;
    private final IdempotencyService idempotency;
    private final EntityManager em;

    public ShipmentService(
        ShipmentRepository s,
        StockTransferRepository t,
        UserRepository u,
        StorageLocationRepository l,
        InventoryMovementService i,
        IdempotencyService idemp,
        EntityManager e
    ) {
        shipments = s;
        transfers = t;
        users = u;
        locations = l;
        inventory = i;
        idempotency = idemp;
        em = e;
    }

    @Transactional
    public ShipmentResponse create(ShipmentRequest r) {
        StockTransfer t = lockTransfer(r.transferId());
        if (t.getStatus() != TransferStatus.PACKED) {
            throw new ConflictException("TRANSFER_NOT_PACKED", "Shipment requires a packed transfer");
        }
        if (shipments.findByTransfer_Id(t.getId()).isPresent()) {
            throw new ConflictException("SHIPMENT_ALREADY_EXISTS", "Transfer already has a shipment");
        }
        Shipment s = new Shipment(number(), t, r.carrierName(), r.trackingNumber(), r.driverName(), r.driverPhone(), r.vehicleNumber(), r.estimatedArrival());
        for (StockTransferItem item : t.getItems()) {
            if (item.getBatch() != null && item.getAllocatedQuantity() > 0) {
                s.addItem(new ShipmentItem(s, item, item.getAllocatedQuantity()));
            }
        }
        return ShipmentResponse.of(shipments.save(s));
    }

    @Transactional
    public ShipmentResponse dispatch(UUID actorId, UUID transferId, String idempotencyKey) {
        return idempotency.execute(actorId, idempotencyKey, "/api/v1/stock-transfers/" + transferId + "/dispatch", null, ShipmentResponse.class, () -> {
            StockTransfer t = lockTransfer(transferId);
            Shipment s = shipments.lockByTransferId(transferId).orElseThrow(() -> new NotFoundException("Shipment"));
            if (s.getStatus() == ShipmentStatus.DISPATCHED || t.getStatus() == TransferStatus.DISPATCHED) {
                return ShipmentResponse.of(s);
            }
            if (s.getStatus() != ShipmentStatus.PREPARING) {
                throw new ConflictException("SHIPMENT_NOT_PREPARING", "Shipment is not ready for dispatch");
            }
            t.transition(TransferStatus.DISPATCHED);
            List<InventoryMovementService.Allocation> a = new ArrayList<>();
            for (StockTransferItem i : t.getItems()) {
                if (i.getBatch() != null) {
                    i.dispatch();
                    a.add(new InventoryMovementService.Allocation(i.getBatch().getId(), i.getDispatchedQuantity(), t.getSourceWarehouse()));
                }
            }
            inventory.dispatch(user(actorId), t.getId(), a);
            s.dispatch();
            return ShipmentResponse.of(s);
        });
    }

    @Transactional
    public Map<String, String> receive(UUID actorId, UUID transferId, ReceiveRequest r, String idempotencyKey) {
        return idempotency.execute(actorId, idempotencyKey, "/api/v1/stock-transfers/" + transferId + "/receive", r, Map.class, () -> {
            StockTransfer t = lockTransfer(transferId);
            Shipment s = shipments.lockByTransferId(transferId).orElseThrow(() -> new NotFoundException("Shipment"));
            if (s.getStatus() == ShipmentStatus.DELIVERED || t.getStatus() == TransferStatus.RECEIVED || t.getStatus() == TransferStatus.COMPLETED || t.getStatus() == TransferStatus.DISCREPANCY_FLAGGED) {
                throw new ConflictException("SHIPMENT_ALREADY_RECEIVED", "Shipment has already been received");
            }
            if (t.getStatus() != TransferStatus.DISPATCHED) {
                throw new ConflictException("TRANSFER_NOT_DISPATCHED", "Transfer has not been dispatched");
            }
            StorageLocation location = locations.findById(r.storageLocationId()).orElseThrow(() -> new NotFoundException("Storage location"));
            if (!location.getWarehouse().getId().equals(t.getDestinationWarehouse().getId())) {
                throw new DomainException("STORAGE_LOCATION_MISMATCH", "Storage location must belong to transfer destination");
            }
            List<InventoryMovementService.Receipt> receipts = new ArrayList<>();
            boolean discrepancy = false;
            for (ReceiveRequest.Item x : r.items()) {
                StockTransferItem i = t.getItems().stream().filter(v -> v.getBatch() != null && v.getBatch().getId().equals(x.batchId())).findFirst().orElseThrow(() -> new DomainException("UNALLOCATED_BATCH", "Received batch was not dispatched"));
                try {
                    i.receive(x.receivedQuantity(), x.damagedQuantity());
                } catch (IllegalArgumentException e) {
                    throw new DomainException("INVALID_RECEIPT_QUANTITY", e.getMessage());
                }
                if (i.getReceivedQuantity() != i.getDispatchedQuantity()) discrepancy = true;
                if (i.getReceivedQuantity() > 0) receipts.add(new InventoryMovementService.Receipt(i.getBatch().getId(), i.getBatch(), i.getReceivedQuantity()));
            }
            if (t.getItems().stream().filter(i -> i.getBatch() != null).anyMatch(i -> i.getReceivedQuantity() + i.getDamagedQuantity() != i.getDispatchedQuantity())) {
                throw new DomainException("INCOMPLETE_RECEIPT", "Each dispatched batch requires reconciliation");
            }
            inventory.receive(user(actorId), t.getId(), t.getDestinationWarehouse(), location, receipts);
            s.deliver();
            t.transition(TransferStatus.RECEIVED);
            if (discrepancy) t.transition(TransferStatus.DISCREPANCY_FLAGGED);
            else t.transition(TransferStatus.COMPLETED);
            return Map.of("status", t.getStatus().name(), "transferId", t.getId().toString(), "shipmentId", s.getId().toString());
        });
    }


    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<ShipmentResponse> list(org.springframework.data.domain.Pageable pageable) {
        return shipments.findAll(pageable).map(ShipmentResponse::of);
    }

    @Transactional(readOnly = true)
    public ShipmentResponse get(UUID id) {
        return ShipmentResponse.of(shipments.findById(id).orElseThrow(() -> new NotFoundException("Shipment")));
    }

    private StockTransfer lockTransfer(UUID id) {
        return transfers.lockById(id).orElseThrow(() -> new NotFoundException("Stock transfer"));
    }

    private User user(UUID id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("User"));
    }

    private String number() {
        Number n = (Number) em.createNativeQuery("select nextval('medtrack_shipment_number_seq')").getSingleResult();
        return "SHP-" + Year.now().getValue() + "-%06d".formatted(n.longValue());
    }
}

