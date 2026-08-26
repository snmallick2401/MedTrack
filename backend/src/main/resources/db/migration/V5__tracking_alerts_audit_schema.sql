CREATE TABLE tracking_events (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), shipment_id UUID NOT NULL REFERENCES shipments(id), milestone_status VARCHAR(32) NOT NULL CHECK(milestone_status IN ('DEPARTED_DEPOT','IN_TRANSIT','CUSTOMS_CLEARED','ARRIVED_HUB','OUT_FOR_DELIVERY','DELIVERED','DELAYED','EXCEPTION')),
 location_name VARCHAR(255) NOT NULL, latitude NUMERIC(9,6), longitude NUMERIC(9,6), remarks TEXT, event_timestamp TIMESTAMPTZ NOT NULL, created_by UUID NOT NULL REFERENCES users(id), created_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE INDEX idx_tracking_events_shipment_timestamp ON tracking_events(shipment_id,event_timestamp);
CREATE TABLE notifications (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), user_id UUID REFERENCES users(id), warehouse_id UUID REFERENCES warehouses(id), type VARCHAR(48) NOT NULL, title VARCHAR(200) NOT NULL, message TEXT NOT NULL, entity_type VARCHAR(64), entity_id UUID, read_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE INDEX idx_notifications_user_read ON notifications(user_id,read_at,created_at DESC);
CREATE INDEX idx_notifications_warehouse_read ON notifications(warehouse_id,read_at,created_at DESC);
