-- Seed comprehensive operational demo data for live dashboard visualization

-- 1. Storage Locations for Central Warehouse (CW01) & Distribution Store North (DS01)
INSERT INTO storage_locations (id, warehouse_id, zone, rack, shelf, bin_code)
VALUES
  ('40000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', 'A', '1', '1', 'A-1-1'),
  ('40000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000001', 'B', '2', '3', 'B-2-3'),
  ('40000000-0000-0000-0000-000000000003', '20000000-0000-0000-0000-000000000001', 'COLD', '1', 'A', 'COLD-01'),
  ('40000000-0000-0000-0000-000000000004', '20000000-0000-0000-0000-000000000002', 'STORE', '1', 'S1', 'S-01')
ON CONFLICT (warehouse_id, bin_code) DO NOTHING;

-- 2. Medicines Catalog
INSERT INTO medicines (id, sku, generic_name, brand_name, category_id, dosage_form, strength, unit_of_measure, storage_temp, min_stock_threshold, min_receiving_shelf_life_days, status, description)
SELECT 
  '50000000-0000-0000-0000-000000000001', 'MED-ANT-00042', 'Amoxicillin Trihydrate', 'Amoxil', id, 'CAPSULE', '500mg', 'BOX_100', '15-25°C', 200, 90, 'ACTIVE', 'Broad-spectrum antibiotic medication.'
FROM medicine_categories WHERE code = 'ANTIBIOTIC'
ON CONFLICT (sku) DO NOTHING;

INSERT INTO medicines (id, sku, generic_name, brand_name, category_id, dosage_form, strength, unit_of_measure, storage_temp, min_stock_threshold, min_receiving_shelf_life_days, status, description)
SELECT 
  '50000000-0000-0000-0000-000000000002', 'MED-ANA-00105', 'Paracetamol', 'Panadol Extra', id, 'TABLET', '650mg', 'BOX_50', '15-30°C', 150, 90, 'ACTIVE', 'Analgesic and antipyretic pain relief.'
FROM medicine_categories WHERE code = 'ANALGESIC'
ON CONFLICT (sku) DO NOTHING;

INSERT INTO medicines (id, sku, generic_name, brand_name, category_id, dosage_form, strength, unit_of_measure, storage_temp, min_stock_threshold, min_receiving_shelf_life_days, status, description)
SELECT 
  '50000000-0000-0000-0000-000000000003', 'MED-VAC-00330', 'Covaxin Inactivated Vaccine', 'Covaxin', id, 'VIAL', '0.5ml', 'VIAL_10', '2-8°C', 100, 180, 'ACTIVE', 'Cold-chain required viral immunization vaccine.'
FROM medicine_categories WHERE code = 'VACCINE'
ON CONFLICT (sku) DO NOTHING;

-- 3. Batches (with active, near-expiry, and cold-chain profiles)
INSERT INTO batches (id, batch_number, medicine_id, supplier_id, manufacturing_date, expiry_date, initial_quantity, status)
VALUES
  ('60000000-0000-0000-0000-000000000001', 'BAT-AMX-2026-01', '50000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000001', CURRENT_DATE - INTERVAL '60 days', CURRENT_DATE + INTERVAL '180 days', 2000, 'ACTIVE'),
  ('60000000-0000-0000-0000-000000000002', 'BAT-PAR-2026-02', '50000000-0000-0000-0000-000000000002', '30000000-0000-0000-0000-000000000001', CURRENT_DATE - INTERVAL '120 days', CURRENT_DATE + INTERVAL '45 days', 1000, 'ACTIVE'),
  ('60000000-0000-0000-0000-000000000003', 'BAT-VAC-2026-03', '50000000-0000-0000-0000-000000000003', '30000000-0000-0000-0000-000000000001', CURRENT_DATE - INTERVAL '30 days', CURRENT_DATE + INTERVAL '240 days', 500, 'ACTIVE')
ON CONFLICT (medicine_id, batch_number) DO NOTHING;

-- 4. Inventory Balances
INSERT INTO inventory_balances (id, warehouse_id, batch_id, storage_location_id, available_quantity, reserved_quantity, quarantined_quantity)
VALUES
  ('70000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', '60000000-0000-0000-0000-000000000001', '40000000-0000-0000-0000-000000000001', 1200, 200, 0),
  ('70000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000001', '60000000-0000-0000-0000-000000000002', '40000000-0000-0000-0000-000000000002', 800, 0, 0),
  ('70000000-0000-0000-0000-000000000003', '20000000-0000-0000-0000-000000000001', '60000000-0000-0000-0000-000000000003', '40000000-0000-0000-0000-000000000003', 350, 0, 0),
  ('70000000-0000-0000-0000-000000000004', '20000000-0000-0000-0000-000000000002', '60000000-0000-0000-0000-000000000001', '40000000-0000-0000-0000-000000000004', 150, 0, 0)
ON CONFLICT (warehouse_id, batch_id) DO NOTHING;

-- 5. Stock Transfers (1 Dispatched in-flight, 1 Requested)
INSERT INTO stock_transfers (id, transfer_number, source_warehouse_id, destination_warehouse_id, status, requested_by, approved_by, notes)
SELECT
  '80000000-0000-0000-0000-000000000001',
  'TR-2026-00101',
  '20000000-0000-0000-0000-000000000001',
  '20000000-0000-0000-0000-000000000002',
  'DISPATCHED',
  u_req.id,
  u_app.id,
  'Monthly regional pharmacy dispensary replenishment.'
FROM (SELECT id FROM users WHERE email IN ('store@medtrack.local', 'admin@medtrack.local') ORDER BY created_at LIMIT 1) u_req
CROSS JOIN (SELECT id FROM users WHERE email IN ('warehouse@medtrack.local', 'admin@medtrack.local') ORDER BY created_at LIMIT 1) u_app
ON CONFLICT (transfer_number) DO NOTHING;

INSERT INTO stock_transfers (id, transfer_number, source_warehouse_id, destination_warehouse_id, status, requested_by, approved_by, notes)
SELECT
  '80000000-0000-0000-0000-000000000002',
  'TR-2026-00102',
  '20000000-0000-0000-0000-000000000001',
  '20000000-0000-0000-0000-000000000002',
  'REQUESTED',
  u_req.id,
  NULL,
  'Urgent winter antibiotic restock requisition.'
FROM (SELECT id FROM users WHERE email IN ('store@medtrack.local', 'admin@medtrack.local') ORDER BY created_at LIMIT 1) u_req
ON CONFLICT (transfer_number) DO NOTHING;

INSERT INTO stock_transfer_items (id, transfer_id, medicine_id, batch_id, requested_quantity, allocated_quantity, picked_quantity, dispatched_quantity, received_quantity)
VALUES
  ('81000000-0000-0000-0000-000000000001', '80000000-0000-0000-0000-000000000001', '50000000-0000-0000-0000-000000000001', '60000000-0000-0000-0000-000000000001', 200, 200, 200, 200, 0),
  ('81000000-0000-0000-0000-000000000002', '80000000-0000-0000-0000-000000000002', '50000000-0000-0000-0000-000000000002', NULL, 150, 0, 0, 0, 0)
ON CONFLICT (id) DO NOTHING;

-- 6. In-Transit Shipment with Live GPS Telemetry
INSERT INTO shipments (id, shipment_number, transfer_id, origin_warehouse_id, destination_warehouse_id, carrier_name, tracking_number, driver_name, driver_phone, vehicle_number, status, estimated_departure, actual_departure, estimated_arrival)
VALUES
  ('90000000-0000-0000-0000-000000000001', 'SH-2026-00091', '80000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000002', 'MediExpress Pharma Logistics', 'TRK-9842104', 'David Chen', '+1-555-0144', 'TRK-REEFER-04', 'IN_TRANSIT', now() - INTERVAL '2 hours', now() - INTERVAL '1 hour 45 minutes', now() + INTERVAL '3 hours')
ON CONFLICT (shipment_number) DO NOTHING;

INSERT INTO shipment_items (id, shipment_id, transfer_item_id, quantity)
VALUES
  ('91000000-0000-0000-0000-000000000001', '90000000-0000-0000-0000-000000000001', '81000000-0000-0000-0000-000000000001', 200)
ON CONFLICT (shipment_id, transfer_item_id) DO NOTHING;

-- GPS Telemetry Waypoints
INSERT INTO tracking_events (id, shipment_id, milestone_status, location_name, latitude, longitude, remarks, event_timestamp, created_by)
SELECT
  '92000000-0000-0000-0000-000000000001',
  '90000000-0000-0000-0000-000000000001',
  'DEPARTED_DEPOT',
  'Boston Central Logistics Hub',
  42.360100,
  -71.058900,
  'Pallets loaded in Reefer Unit 4. Temp verified at 3.8°C.',
  now() - INTERVAL '1 hour 45 minutes',
  u.id
FROM (SELECT id FROM users ORDER BY created_at LIMIT 1) u
ON CONFLICT (id) DO NOTHING;

INSERT INTO tracking_events (id, shipment_id, milestone_status, location_name, latitude, longitude, remarks, event_timestamp, created_by)
SELECT
  '92000000-0000-0000-0000-000000000002',
  '90000000-0000-0000-0000-000000000001',
  'IN_TRANSIT',
  'I-90 Highway Mile Marker 42',
  42.271100,
  -71.417800,
  'Vehicle in motion. GPS signal steady, cargo temp stable.',
  now() - INTERVAL '30 minutes',
  u.id
FROM (SELECT id FROM users ORDER BY created_at LIMIT 1) u
ON CONFLICT (id) DO NOTHING;

-- 7. Operational Notifications & Alerts
INSERT INTO notifications (id, user_id, warehouse_id, type, title, message, entity_type, entity_id)
SELECT
  '93000000-0000-0000-0000-000000000001',
  u.id,
  '20000000-0000-0000-0000-000000000001',
  'NEAR_EXPIRY',
  'Batch Approaching 45-Day Expiry Window',
  'Paracetamol 650mg (Batch BAT-PAR-2026-02) expires in 45 days. Prioritize FEFO allocation.',
  'BATCH',
  '60000000-0000-0000-0000-000000000002'
FROM (SELECT id FROM users ORDER BY created_at LIMIT 1) u
ON CONFLICT (id) DO NOTHING;

INSERT INTO notifications (id, user_id, warehouse_id, type, title, message, entity_type, entity_id)
SELECT
  '93000000-0000-0000-0000-000000000002',
  u.id,
  '20000000-0000-0000-0000-000000000001',
  'TRANSFER_DISPATCHED',
  'Inter-Facility Transfer TR-2026-00101 In Transit',
  'Shipment SH-2026-00091 departed Central Warehouse for Distribution Store North.',
  'SHIPMENT',
  '90000000-0000-0000-0000-000000000001'
FROM (SELECT id FROM users ORDER BY created_at LIMIT 1) u
ON CONFLICT (id) DO NOTHING;
