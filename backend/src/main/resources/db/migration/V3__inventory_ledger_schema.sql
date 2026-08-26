CREATE SEQUENCE medtrack_journal_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE inventory_balances (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  warehouse_id UUID NOT NULL REFERENCES warehouses(id),
  batch_id UUID NOT NULL REFERENCES batches(id),
  storage_location_id UUID NOT NULL REFERENCES storage_locations(id),
  available_quantity INTEGER NOT NULL DEFAULT 0 CHECK (available_quantity >= 0),
  reserved_quantity INTEGER NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
  quarantined_quantity INTEGER NOT NULL DEFAULT 0 CHECK (quarantined_quantity >= 0),
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_inventory_balance_warehouse_batch UNIQUE (warehouse_id, batch_id)
);
CREATE INDEX idx_inventory_balances_warehouse_batch ON inventory_balances (warehouse_id, batch_id);
CREATE INDEX idx_inventory_balances_storage_location ON inventory_balances (storage_location_id);

CREATE TABLE inventory_journal_entries (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  entry_number VARCHAR(32) NOT NULL UNIQUE,
  entry_type VARCHAR(32) NOT NULL CHECK (entry_type IN ('INBOUND_RECEIPT','TRANSFER_DISPATCH','TRANSFER_RECEIVE','STOCK_ADJUSTMENT','DISPENSE','WRITE_OFF','QUARANTINE_TRANSFER')),
  reference_entity_type VARCHAR(64) NOT NULL,
  reference_entity_id UUID NOT NULL,
  performed_by UUID NOT NULL REFERENCES users(id),
  description TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_inventory_journal_entries_reference ON inventory_journal_entries (reference_entity_type, reference_entity_id);
CREATE INDEX idx_inventory_journal_entries_performed_by ON inventory_journal_entries (performed_by);

CREATE TABLE inventory_ledger_lines (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  journal_entry_id UUID NOT NULL REFERENCES inventory_journal_entries(id),
  batch_id UUID NOT NULL REFERENCES batches(id),
  account_type VARCHAR(32) NOT NULL CHECK (account_type IN ('WAREHOUSE_ACTIVE','IN_TRANSIT','SUPPLIER_OFFSET','DISPENSE_EXPENSE','WRITE_OFF_LOSS','AUDIT_SURPLUS_OFFSET','QUARANTINE_HOLD')),
  warehouse_id UUID REFERENCES warehouses(id),
  direction VARCHAR(8) NOT NULL CHECK (direction IN ('DEBIT','CREDIT')),
  quantity INTEGER NOT NULL CHECK (quantity > 0),
  available_before INTEGER, available_delta INTEGER, available_after INTEGER,
  reserved_before INTEGER, reserved_delta INTEGER, reserved_after INTEGER,
  quarantined_before INTEGER, quarantined_delta INTEGER, quarantined_after INTEGER,
  balance_after INTEGER,
  CONSTRAINT chk_ledger_available_snapshot CHECK (available_after IS NULL OR available_after = available_before + available_delta),
  CONSTRAINT chk_ledger_reserved_snapshot CHECK (reserved_after IS NULL OR reserved_after = reserved_before + reserved_delta),
  CONSTRAINT chk_ledger_quarantined_snapshot CHECK (quarantined_after IS NULL OR quarantined_after = quarantined_before + quarantined_delta),
  CONSTRAINT chk_ledger_physical_snapshot CHECK (balance_after IS NULL OR balance_after = available_after + reserved_after + quarantined_after)
);
CREATE INDEX idx_inventory_ledger_lines_journal ON inventory_ledger_lines (journal_entry_id);
CREATE INDEX idx_inventory_ledger_lines_batch ON inventory_ledger_lines (batch_id);
CREATE INDEX idx_inventory_ledger_lines_warehouse ON inventory_ledger_lines (warehouse_id);

CREATE TABLE idempotency_keys (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  idempotency_key VARCHAR(255) NOT NULL,
  user_id UUID NOT NULL REFERENCES users(id),
  request_path VARCHAR(255) NOT NULL,
  request_fingerprint VARCHAR(64) NOT NULL,
  response_status INTEGER,
  response_body TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_idempotency_key_user_path UNIQUE (idempotency_key, user_id, request_path)
);
CREATE INDEX idx_idempotency_keys_expires_at ON idempotency_keys (expires_at);

CREATE TABLE audit_logs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id),
  action VARCHAR(80) NOT NULL,
  entity_name VARCHAR(80) NOT NULL,
  entity_id UUID NOT NULL,
  changes_json TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_name, entity_id);
CREATE INDEX idx_audit_logs_user ON audit_logs (user_id);

CREATE OR REPLACE FUNCTION medtrack_reject_ledger_mutation() RETURNS trigger AS $$ BEGIN RAISE EXCEPTION 'Inventory ledger is append-only'; END; $$ LANGUAGE plpgsql;
CREATE TRIGGER trg_reject_journal_mutation BEFORE UPDATE OR DELETE ON inventory_journal_entries FOR EACH ROW EXECUTE FUNCTION medtrack_reject_ledger_mutation();
CREATE TRIGGER trg_reject_ledger_line_mutation BEFORE UPDATE OR DELETE ON inventory_ledger_lines FOR EACH ROW EXECUTE FUNCTION medtrack_reject_ledger_mutation();
CREATE TRIGGER trg_reject_audit_mutation BEFORE UPDATE OR DELETE ON audit_logs FOR EACH ROW EXECUTE FUNCTION medtrack_reject_ledger_mutation();
