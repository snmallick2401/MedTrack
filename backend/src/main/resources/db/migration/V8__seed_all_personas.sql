-- Seed user accounts for all 5 enterprise operational personas
INSERT INTO users (id, email, password_hash, full_name, role_id, assigned_warehouse_id, status)
VALUES
  ('00000000-0000-0000-0000-000000000012', 'warehouse@medtrack.local', '$2a$12$EmZS6ONFKD9dP1BqJt9h5uo2y8Igm62F5MeCb7B6HugyUK6/ZCzJ.', 'Central Warehouse Manager', '00000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000001', 'ACTIVE'),
  ('00000000-0000-0000-0000-000000000013', 'store@medtrack.local', '$2a$12$EmZS6ONFKD9dP1BqJt9h5uo2y8Igm62F5MeCb7B6HugyUK6/ZCzJ.', 'Dispensary Store Lead', '00000000-0000-0000-0000-000000000003', '20000000-0000-0000-0000-000000000002', 'ACTIVE'),
  ('00000000-0000-0000-0000-000000000014', 'logistics@medtrack.local', '$2a$12$EmZS6ONFKD9dP1BqJt9h5uo2y8Igm62F5MeCb7B6HugyUK6/ZCzJ.', 'Fleet & Logistics Coordinator', '00000000-0000-0000-0000-000000000004', '20000000-0000-0000-0000-000000000001', 'ACTIVE'),
  ('00000000-0000-0000-0000-000000000015', 'auditor@medtrack.local', '$2a$12$EmZS6ONFKD9dP1BqJt9h5uo2y8Igm62F5MeCb7B6HugyUK6/ZCzJ.', 'GxP Quality & Compliance Auditor', '00000000-0000-0000-0000-000000000005', NULL, 'ACTIVE')
ON CONFLICT (email) DO NOTHING;
