package com.medtrack.shipment.repository;

import com.medtrack.shipment.entity.Shipment;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Shipment s where s.transfer.id=:transferId")
    Optional<Shipment> lockByTransferId(@Param("transferId") UUID transferId);

    Optional<Shipment> findByTransfer_Id(UUID transferId);

    @Query(value = "select s from Shipment s where s.origin.id = :warehouseId or s.destination.id = :warehouseId",
           countQuery = "select count(s) from Shipment s where s.origin.id = :warehouseId or s.destination.id = :warehouseId")
    Page<Shipment> findByWarehouse(@Param("warehouseId") UUID warehouseId, Pageable pageable);
}
