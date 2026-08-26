package com.medtrack.shipment.repository;

import com.medtrack.shipment.entity.ShipmentItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentItemRepository extends JpaRepository<ShipmentItem, UUID> {
    List<ShipmentItem> findByShipment_Id(UUID shipmentId);
}