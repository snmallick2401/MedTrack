CREATE SEQUENCE medtrack_transfer_number_seq START WITH 1;
CREATE SEQUENCE medtrack_shipment_number_seq START WITH 1;

CREATE TABLE stock_transfers (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), transfer_number VARCHAR(32) NOT NULL UNIQUE,
 source_warehouse_id UUID NOT NULL REFERENCES warehouses(id), destination_warehouse_id UUID NOT NULL REFERENCES warehouses(id),
 status VARCHAR(32) NOT NULL CHECK (status IN ('DRAFT','REQUESTED','APPROVED','ALLOCATED','PICKED','PACKED','DISPATCHED','RECEIVED','COMPLETED','REJECTED','CANCELLED','DISCREPANCY_FLAGGED')),
 requested_by UUID NOT NULL REFERENCES users(id), approved_by UUID REFERENCES users(id), notes TEXT,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CONSTRAINT chk_transfer_distinct_warehouses CHECK (source_warehouse_id <> destination_warehouse_id)
);
CREATE INDEX idx_stock_transfers_source_status ON stock_transfers(source_warehouse_id,status);
CREATE INDEX idx_stock_transfers_destination_status ON stock_transfers(destination_warehouse_id,status);

CREATE TABLE stock_transfer_items (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), transfer_id UUID NOT NULL REFERENCES stock_transfers(id), medicine_id UUID NOT NULL REFERENCES medicines(id), batch_id UUID REFERENCES batches(id),
 requested_quantity INTEGER NOT NULL CHECK(requested_quantity > 0), allocated_quantity INTEGER NOT NULL DEFAULT 0 CHECK(allocated_quantity >= 0), picked_quantity INTEGER NOT NULL DEFAULT 0 CHECK(picked_quantity >= 0), dispatched_quantity INTEGER NOT NULL DEFAULT 0 CHECK(dispatched_quantity >= 0), received_quantity INTEGER NOT NULL DEFAULT 0 CHECK(received_quantity >= 0), damaged_quantity INTEGER NOT NULL DEFAULT 0 CHECK(damaged_quantity >= 0),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_transfer_items_transfer ON stock_transfer_items(transfer_id);
CREATE INDEX idx_transfer_items_batch ON stock_transfer_items(batch_id);

CREATE TABLE shipments (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), shipment_number VARCHAR(32) NOT NULL UNIQUE, transfer_id UUID NOT NULL UNIQUE REFERENCES stock_transfers(id),
 origin_warehouse_id UUID NOT NULL REFERENCES warehouses(id), destination_warehouse_id UUID NOT NULL REFERENCES warehouses(id), carrier_name VARCHAR(160) NOT NULL, tracking_number VARCHAR(160) NOT NULL UNIQUE,
 driver_name VARCHAR(160), driver_phone VARCHAR(40), vehicle_number VARCHAR(80), status VARCHAR(32) NOT NULL CHECK(status IN ('PREPARING','DISPATCHED','IN_TRANSIT','OUT_FOR_DELIVERY','DELIVERED','DELAYED','EXCEPTION_FAILED','CANCELLED')),
 estimated_departure TIMESTAMPTZ, actual_departure TIMESTAMPTZ, estimated_arrival TIMESTAMPTZ NOT NULL, actual_arrival TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), CONSTRAINT chk_shipment_distinct_warehouses CHECK(origin_warehouse_id <> destination_warehouse_id)
);
CREATE INDEX idx_shipments_origin_status ON shipments(origin_warehouse_id,status);
CREATE INDEX idx_shipments_destination_status ON shipments(destination_warehouse_id,status);

CREATE TABLE shipment_items (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), shipment_id UUID NOT NULL REFERENCES shipments(id), transfer_item_id UUID NOT NULL REFERENCES stock_transfer_items(id), quantity INTEGER NOT NULL CHECK(quantity > 0), created_at TIMESTAMPTZ NOT NULL DEFAULT now(), CONSTRAINT uq_shipment_item_transfer_item UNIQUE(shipment_id,transfer_item_id));
CREATE INDEX idx_shipment_items_shipment ON shipment_items(shipment_id);
