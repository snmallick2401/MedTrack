package com.medtrack.shipment.entity;

import com.medtrack.transfer.entity.StockTransferItem;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "shipment_items")
@EntityListeners(AuditingEntityListener.class)
public class ShipmentItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_id", nullable = false)
    private Shipment shipment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_item_id", nullable = false)
    private StockTransferItem transferItem;

    @Column(nullable = false)
    private int quantity;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ShipmentItem() {}

    public ShipmentItem(Shipment shipment, StockTransferItem transferItem, int quantity) {
        this.shipment = shipment;
        this.transferItem = transferItem;
        this.quantity = quantity;
    }

    public void setShipment(Shipment shipment) {
        this.shipment = shipment;
    }

    public UUID getId() { return id; }
    public Shipment getShipment() { return shipment; }
    public StockTransferItem getTransferItem() { return transferItem; }
    public int getQuantity() { return quantity; }
    public Instant getCreatedAt() { return createdAt; }
}