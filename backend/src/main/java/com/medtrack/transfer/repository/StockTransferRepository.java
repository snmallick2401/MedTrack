package com.medtrack.transfer.repository;

import com.medtrack.transfer.entity.StockTransfer;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface StockTransferRepository extends JpaRepository<StockTransfer, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select distinct t from StockTransfer t left join fetch t.items where t.id=:id")
    Optional<StockTransfer> lockById(@Param("id") UUID id);

    @Query(value = "select t from StockTransfer t where t.sourceWarehouse.id = :warehouseId or t.destinationWarehouse.id = :warehouseId",
           countQuery = "select count(t) from StockTransfer t where t.sourceWarehouse.id = :warehouseId or t.destinationWarehouse.id = :warehouseId")
    Page<StockTransfer> findByWarehouse(@Param("warehouseId") UUID warehouseId, Pageable pageable);
}
