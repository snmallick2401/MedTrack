package com.medtrack.shipment.dto;

import com.medtrack.shipment.entity.Shipment;
import java.util.*;

public record ShipmentResponse(
    UUID id,
    String shipmentNumber,
    String status,
    UUID transferId,
    String carrierName,
    String trackingNumber,
    List<Item> items
) {
    public record Item(
        UUID shipmentItemId,
        UUID transferItemId,
        UUID batchId,
        String batchNumber,
        UUID medicineId,
        String medicineName,
        int quantity
    ) {}

    public static ShipmentResponse of(Shipment s) {
        List<Item> itemList = s.getItems() == null ? List.of() : s.getItems().stream().map(item -> {
            var ti = item.getTransferItem();
            var batch = ti.getBatch();
            var med = ti.getMedicine();
            return new Item(
                item.getId(),
                ti.getId(),
                batch != null ? batch.getId() : null,
                batch != null ? batch.getBatchNumber() : null,
                med != null ? med.getId() : null,
                med != null ? med.getGenericName() : null,
                item.getQuantity()
            );
        }).toList();

        return new ShipmentResponse(
            s.getId(),
            s.getShipmentNumber(),
            s.getStatus().name(),
            s.getTransfer().getId(),
            s.getCarrierName(),
            s.getTrackingNumber(),
            itemList
        );
    }
}

