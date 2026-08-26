package com.medtrack.inventory.entity;
import com.medtrack.batch.entity.Batch; import com.medtrack.shared.model.BaseEntity; import com.medtrack.warehouse.entity.*; import jakarta.persistence.*;
@Entity @Table(name="inventory_balances") public class InventoryBalance extends BaseEntity {
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="warehouse_id",nullable=false) private Warehouse warehouse;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="batch_id",nullable=false) private Batch batch;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="storage_location_id",nullable=false) private StorageLocation storageLocation;
 @Column(name="available_quantity",nullable=false) private int availableQuantity;
 @Column(name="reserved_quantity",nullable=false) private int reservedQuantity;
 @Column(name="quarantined_quantity",nullable=false) private int quarantinedQuantity;
 @Version @Column(nullable=false) private long version;
 protected InventoryBalance(){} public InventoryBalance(Warehouse w,Batch b,StorageLocation s){warehouse=w;batch=b;storageLocation=s;}
 public synchronized void receive(int quantity){availableQuantity=Math.addExact(availableQuantity,quantity);} public synchronized void reserve(int quantity){if(quantity<=0||availableQuantity<quantity)throw new IllegalArgumentException("Insufficient available stock");availableQuantity-=quantity;reservedQuantity+=quantity;}
 public Warehouse getWarehouse(){return warehouse;} public Batch getBatch(){return batch;} public StorageLocation getStorageLocation(){return storageLocation;} public int getAvailableQuantity(){return availableQuantity;} public int getReservedQuantity(){return reservedQuantity;} public int getQuarantinedQuantity(){return quarantinedQuantity;} public int getPhysicalQuantity(){return availableQuantity+reservedQuantity+quarantinedQuantity;} public long getVersion(){return version;}
}
