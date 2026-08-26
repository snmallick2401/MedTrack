# MedTrack — Product Requirements Document (PRD)

**Document Version:** 1.0.0  
**Status:** Approved for Implementation  
**Target System:** MedTrack Operational Platform (Web)  
**Author:** MedTrack Systems & Architecture Team  

---

## 1. Product Overview

**MedTrack** is an enterprise-grade operational management system designed to track, manage, and trace medicine inventory across central warehouses, regional distribution stores, and transportation routes. 

Unlike generic inventory systems, MedTrack is purpose-built for pharmaceutical supply chains. It enforces strict **Batch-Level Traceability**, **First Expired, First Out (FEFO)** allocation, **Double-Entry Stock Ledgers**, and real-time **Transportation & Shipment Milestones**.

The central business topology is:

```text
       ┌────────────────────────────────────────────────────────┐
       │     Central Warehouse (Primary Inbound / Storage)      │
       └───────────────────────────┬────────────────────────────┘
                                   │
                    ┌──────────────┴──────────────┐
                    ▼                             ▼
       ┌─────────────────────────┐   ┌─────────────────────────┐
       │ Store / Distribution A  │   │ Store / Distribution B  │
       │     (Local Inventory)   │   │     (Local Inventory)   │
       └────────────┬────────────┘   └────────────┬────────────┘
                    │                             │
                    ▼                             ▼
             Inventory Usage               Inventory Usage
          (Dispense / Write-off)        (Dispense / Write-off)
```

The system provides absolute visibility into the medicine lifecycle: from receiving supplier consignments at the Central Warehouse, to packing and transit tracking via intermediate carriers, to receiving at local stores and final consumption or write-off.

---

## 2. Problem Statement

Pharmaceutical supply chains suffer from critical operational challenges that generic ERPs fail to solve effectively:

1. **High Spoilage & Expiry Losses:** Medicines expire on warehouse shelves due to lack of enforced FEFO (First Expired, First Out) picking protocols.
2. **Stockouts & Imbalanced Distribution:** Regional clinics/stores run out of essential drugs while central warehouses hold excess stock without real-time transfer visibility.
3. **In-Transit Blind Spots:** Once goods leave a central depot, dispatchers and store managers have no reliable view of shipment status, driver milestones, or transit delays.
4. **Audit Failures & Counterfeit Vulnerabilities:** Inability to track a specific batch number back to its origin supplier, receiving inspection, and transit chain of custody.
5. **Data Inconsistencies & Silent Stock Mutations:** Traditional CRUD systems mutate stock quantity fields directly in database tables without an immutable ledger, making forensic audit and discrepancy reconciliation impossible.

MedTrack resolves these problems with strict domain-driven constraints, immutable transaction ledgers, barcode/QR-assisted workflows, and end-to-end shipment lifecycle tracking.

---

## 3. Goals and Non-Goals

### 3.1 Goals
- **Batch-Level Traceability:** Track every unit of medicine by SKU, Batch Number, Manufacturing Date, and Expiry Date.
- **Enforced FEFO Dispatch:** Automate stock picking recommendations based on earliest expiration dates to minimize product wastage.
- **Immutable Inventory Accounting:** Implement double-entry inventory transactions where stock balances are deterministic and fully auditable.
- **End-to-End Shipment Visibility:** Provide structured shipment states (Draft, Allocated, Picked, Dispatched, In-Transit, Delivered) with carrier milestone tracking and map visualizations.
- **Proactive Operational Alerts:** Automated alerts for low stock thresholds, near-expiry batches (30/60/90 days), and shipment transit delays.
- **Role-Based Access Control (RBAC):** Strict operational segregation of duties between Central Warehouse Managers, Store Clerks, Logistics Dispatchers, and Compliance Auditors.
- **Fast, Barcode/QR-Assisted Operations:** Support camera and handheld scanner workflows for receiving, picking, packing, and stock verification.

### 3.2 Non-Goals
- **Not an E-Commerce / Consumer Pharmacy:** MedTrack does not process consumer-facing online retail orders or payment gateway checkouts.
- **Not a Point of Sale (POS) Cash Register:** MedTrack tracks operational dispensing and stock usage, not retail pricing, sales tax, or credit card transactions.
- **Not a Clinical Electronic Health Record (EHR):** MedTrack does not manage patient diagnostic medical records, doctor prescriptions, or insurance billing claims.
- **Not an IoT Sensor Telemetry Streamer:** In MVP, temperature/humidity sensor hardware streams are out-of-scope; cold-chain status is captured via operational milestone flags.

---

## 4. Target Users and User Roles

| Role Name | Scope & Authority | Primary Responsibilities |
| :--- | :--- | :--- |
| **`SUPER_ADMIN`** | Global System | System configuration, user provisioning, role assignments, master medicine taxonomy, global audit trail review. |
| **`CENTRAL_WAREHOUSE_MANAGER`** | Central Warehouse | Inbound receiving from suppliers, batch creation, warehouse bin management, transfer request approval, FEFO picking, packing, and dispatching. |
| **`STORE_MANAGER`** | Assigned Store(s) | Local store inventory monitoring, creating stock transfer requests, receiving inbound shipments, performing physical stock reconciliations, recording local dispensing/usage. |
| **`LOGISTICS_COORDINATOR`** | Global / Transit | Carrier management, shipment manifest creation, tracking number assignment, milestone logging, delay exception reporting. |
| **`AUDITOR`** | Global (Read-Only) | Comprehensive read-only access to inventory ledgers, historical transfers, discrepancy logs, and compliance reporting. |

---

## 5. User Personas

### Persona 1: Elena Rostova — Central Warehouse Supervisor
- **Background:** Manages a 50,000 sq. ft. central pharmaceutical depot receiving 30+ supplier consignments weekly.
- **Pain Points:** Spends hours manually checking batch expiry dates; struggles to ensure warehouse floor staff pick the oldest batches first; handles frequent telephone calls from branch stores asking when shipments will arrive.
- **Needs in MedTrack:** One-click inbound batch receiving with auto-generated QR labels; automated FEFO batch allocation on transfer orders; instant dispatch manifests.

### Persona 2: Marcus Chen — Store Pharmacist & Inventory Lead
- **Background:** Manages inventory at a busy metropolitan regional distribution dispensary.
- **Pain Points:** Unpredictable delivery arrival times; manual paper-based receiving prone to count discrepancies; sudden discovery of expired stock at the back of shelves.
- **Needs in MedTrack:** Real-time transfer request workflow; clear visibility of incoming in-transit shipments; automated 30/60/90-day expiry warning dashboard.

### Persona 3: Tariq Mansoor — Logistics & Fleet Coordinator
- **Background:** Coordinates dispatch and third-party freight carriers across 12 distribution routes.
- **Pain Points:** Disjointed tracking numbers across different carriers; no central map to identify delayed shipments; endless emails notifying stores of transit delays.
- **Needs in MedTrack:** Unified shipment dashboard with tracking number integration, route milestone recording, and one-click transit exception/delay broadcasting.

---

## 6. Core User Journeys

### 6.1 Inbound Consignment Receiving (Supplier to Central Warehouse)
1. Central Warehouse Manager navigates to **Inbound Receiving**.
2. Selects Supplier, enters Purchase Order reference, and adds medicine items.
3. For each medicine, enters **Batch Number**, **Quantity**, **Manufacturing Date**, **Expiry Date**, and assigns **Storage Bin/Location**.
4. System validates that Expiry Date is in the future (> 90 days from receipt).
5. Manager confirms receipt.
6. System atomically creates Batch records, inserts an `INBOUND_RECEIPT` transaction in the ledger, increments Central Warehouse stock balance, and generates printable QR batch labels.

### 6.2 Stock Transfer Lifecycle (Central Warehouse to Regional Store)

```text
[Store Manager]                [System / FEFO]               [Central Depot]               [Carrier / Driver]             [Store Manager]
Create Transfer Request ─────► Validate Stock Availability
                               & Reserve via FEFO ──────────► Pick & Pack Items ──────────► Dispatch & Assign Carrier ──► Track In-Transit
                                                                                            (Generate Manifest & QR)       Milestones
                                                                                                                               │
                                                                                                                               ▼
[Store Completed] ◄─────────── Settle Discrepancies (if any) ◄──────────────────────────────────────────────────────── Receive & Scan QR
(Stock Available in Store)    & Create Ledger Transactions
```

1. **Request:** Store Manager submits a transfer request for 200 units of *Amoxicillin 500mg*.
2. **Allocation:** System verifies Central Warehouse available stock (Total Stock minus active Reservations) and allocates batches using **FEFO** (e.g., Batch B101 with expiry Oct 2026 before Batch B102 with expiry Dec 2026).
3. **Pick & Pack:** Central Warehouse staff picks the exact batches indicated on the pick-list, scans QR codes to verify, and marks status as `PACKED`.
4. **Dispatch:** Logistics Coordinator attaches carrier details (e.g., Courier Name, Tracking # `TRK-88219`, Origin Coordinates, Destination Coordinates) and transitions status to `DISPATCHED`.
5. **In-Transit:** System records tracking milestones (e.g., *Departed Central Depot*, *In Transit at Waypoint Alpha*).
6. **Receipt & Reconciliation:** Store Manager scans the shipment QR upon physical arrival, inspects delivered items, enters received quantities (and records any damaged/missing units), and confirms receipt.
7. **Settlement:** System moves inventory from in-transit ledger to active Store stock balance, closes the shipment, and generates discrepancy audit records if received count $\neq$ dispatched count.

---

## 7. Functional Requirements (MoSCoW Prioritization)

```text
MUST HAVE (MVP)
├── Core RBAC & JWT Authentication
├── Medicine Taxonomy & Master Catalog
├── Batch-level Management & Expiry Tracking
├── Central Warehouse & Multi-Store Inventory Management
├── Double-entry Immutable Transaction Ledger
├── FEFO Batch Allocation Algorithm
├── End-to-End Stock Transfer Workflow (Request -> Reserve -> Pick -> Dispatch -> Receive)
├── Shipment & In-Transit Milestone Tracking
├── QR / Barcode Generation and Camera Scanning
├── Low Stock & Expiry Operational Alerts
└── Audit Logging for All Mutations

SHOULD HAVE (Phase 2 / Fast Follow)
├── External Geocoding & Route Map Visualizations (Leaflet / OpenStreetMap)
├── Shipment Tracking Aggregator Adapter (AfterShip / Carrier Webhooks)
├── Batch Quarantine & Damaged Stock Disposal Workflow
├── Automated Email Notifications (SMTP / JavaMail)
└── CSV/Excel & PDF Manifest/Report Exports

COULD HAVE (Phase 3 / Future)
├── Cold-Chain Temperature Logging at Milestones
├── Multi-Stop Consolidated Shipment Runs
├── Offline-capable PWA Scanner for Warehouse Floors
└── Automated Reorder Recommendations based on Consumption Velocity

OUT OF SCOPE
├── Consumer E-commerce Checkout
├── Direct POS Cash Register Billing
├── Patient EHR Prescriptions & Insurance Claims
└── Real-time Bluetooth/IoT hardware telemetry stream processing
```

---

## 8. Detailed Domain Requirements

### 8.1 Medicine Management
- **FR-MED-01 [MUST]:** System shall maintain master records for medicines with fields: SKU (unique internal identifier), Generic Name, Brand Name, Category (e.g., Antibiotics, Analgesics, Cardiovascular), Dosage Form (e.g., Tablet, Syrup, Injection), Strength (e.g., 500mg, 10mg/ml), Unit of Measure (Box, Bottle, Vial, Blister Pack), Storage Temperature Requirement (Ambient 15–25°C, Refrigerated 2–8°C, Frozen < -10°C), and Description.
- **FR-MED-02 [MUST]:** Medicines can be marked as `ACTIVE` or `DISCONTINUED`. Discontinued medicines cannot be used in new transfer requests or supplier receipts.
- **FR-MED-03 [MUST]:** Medicine SKU must follow a standardized format: `MED-[CATEGORY_CODE]-[SEQUENTIAL_ID]` (e.g., `MED-ANT-00042`).

### 8.2 Batch & Expiry Management
- **FR-BAT-01 [MUST]:** Every medicine inventory unit must be tied to a specific `Batch`.
- **FR-BAT-02 [MUST]:** Batch attributes must include: Batch Number (alphanumeric, e.g., `BAT-2026-08A`), Medicine Reference, Manufacturing Date, Expiry Date, Initial Received Quantity, Current Available Quantity, Status (`ACTIVE`, `QUARANTINED`, `EXPIRED`, `DEPLETED`), and Supplier Reference.
- **FR-BAT-03 [MUST]:** The system must validate inbound batch receiving against the medicine's configurable shelf-life requirement:
  $$\text{Expiry Date} \ge \text{Current Date} + \text{medicine.min\_receiving\_shelf\_life\_days}$$
  *(System default fallback: 90 days).*
- **FR-BAT-04 [MUST]:** Dynamic Batch Health Classification:
  - **Good:** Expiry date > 90 days away.
  - **Expiring Soon (Warning):** Expiry date between 31 and 90 days away.
  - **Critical Expiry:** Expiry date $\le$ 30 days away.
  - **Expired:** Expiry date $<$ Current Date (automatically locked against dispatch).

### 8.3 Warehouse & Multi-Location Inventory Ledger
- **FR-INV-01 [MUST]:** The system must distinguish between `CENTRAL_WAREHOUSE` and regional `DISTRIBUTION_STORE` locations.
- **FR-INV-02 [MUST]:** Each location has designated storage zones and bins (e.g., Zone A, Rack 03, Shelf 02).
- **FR-INV-03 [MUST]:** Stock calculations must maintain strict mathematical invariant:
  $$\text{Total Physical Stock} = \text{Available Stock} + \text{Reserved Stock} + \text{Quarantined Stock}$$
- **FR-INV-04 [MUST]:** Inventory mutations must be recorded as balanced **Double-Entry Journal Entries** (`inventory_journal_entries`) containing paired **Debit and Credit Ledger Lines** (`inventory_ledger_lines`) with explicit 3-bucket state snapshots (`available`, `reserved`, `quarantined` before/delta/after) and strict balancing invariant:
  $$\sum \text{Debit Quantities} = \sum \text{Credit Quantities}$$
  - **Asset Accounts:** `WAREHOUSE_ACTIVE` (Physical stock), `IN_TRANSIT` (En-route on carrier), `QUARANTINE_HOLD` (QA hold).
  - **Offset & Expense Accounts:** `SUPPLIER_OFFSET` (Inbound supplier clearing), `DISPENSE_EXPENSE` (Patient dispensing), `WRITE_OFF_LOSS` (Damage/expiry loss), `AUDIT_SURPLUS_OFFSET` (Cycle count surplus).
  - A fast materialized snapshot table (`inventory_balances`) is synchronized with `@Version` optimistic locking.
- **FR-INV-05 [MUST]:** Stock balances cannot become negative under any circumstance (`CHECK (available_quantity >= 0)`).

### 8.4 FEFO Batch Allocation Algorithm
- **FR-FEFO-01 [MUST]:** When allocating stock for transfer requests, the system must execute automated **FEFO (First Expired, First Out)** sorting:
  $$\text{Order by: } \text{batch.expiry\_date ASC, batch.id ASC}$$
- **FR-FEFO-02 [MUST]:** Batches with status `QUARANTINED` or `EXPIRED` must be excluded from automated allocation.
- **FR-FEFO-03 [MUST]:** Manual allocation is **never allowed to bypass FEFO silently**. When an authorized Central Warehouse Manager or Super Admin manually overrides FEFO batch picking, the request must provide a mandatory `override_reason`, record `overridden_by` and `overridden_at` on the transfer item, and commit a synchronous critical audit record.

### 8.5 Stock Transfer Lifecycle
- **FR-TRF-01 [MUST]:** Transfer requests must follow an explicit, deterministic state machine:
  $$\text{DRAFT} \longrightarrow \text{REQUESTED} \longrightarrow \text{APPROVED} \longrightarrow \text{PICKING} \longrightarrow \text{PACKED} \longrightarrow \text{DISPATCHED} \longrightarrow \text{RECEIVED} \longrightarrow \text{COMPLETED}$$
  *(With branch states: `REJECTED`, `CANCELLED`, `DISCREPANCY_FLAGGED`).*
- **FR-TRF-02 [MUST]:** Approving a transfer request immediately creates stock reservations in the source warehouse to prevent double-allocation under concurrent requests.
- **FR-TRF-03 [MUST]:** Cancelling an approved/requested transfer must automatically release all reserved stock.
- **FR-TRF-04 [MUST]:** Receiving a transfer at the destination store requires entering the actual count received per batch. If $\text{Received Count} < \text{Dispatched Count}$, the transfer enters `DISCREPANCY_FLAGGED` state and requires mandatory manager sign-off.

### 8.6 Shipment & Transportation Tracking
- **FR-SHP-01 [MUST]:** Every physical dispatch generates a `Shipment` record with attributes: Shipment Number (e.g., `SHP-2026-0091`), Associated Transfer ID, Origin Warehouse, Destination Store, Carrier Name, Tracking Reference Number, Vehicle / Driver details, Estimated Time of Arrival (ETA), and Actual Arrival Time.
- **FR-SHP-02 [MUST]:** Shipment Status progression:
  $$\text{PREPARING} \longrightarrow \text{DISPATCHED} \longrightarrow \text{IN\_TRANSIT} \longrightarrow \text{OUT\_FOR\_DELIVERY} \longrightarrow \text{DELIVERED}$$
  *(Exception states: `DELAYED`, `EXCEPTION_FAILED`, `CANCELLED`).*
- **FR-SHP-03 [MUST]:** Logistics Coordinators and Drivers can log discrete `TrackingEvent` records: Timestamp, Location Name, Latitude, Longitude, Milestone Status, and Event Remarks.
- **FR-SHP-04 [SHOULD]:** An interactive map must render the route polyline between Origin and Destination with active waypoint markers and real-time status color coding.

### 8.7 Barcode & QR Code Workflows
- **FR-QR-01 [MUST]:** The system must generate standard ISO/IEC 18004 compliant QR codes and Code-128 linear barcodes for:
  - **Batch Labels:** Payload contains JSON or formatted URI with SKU, Batch #, and Expiry Date.
  - **Shipment Labels:** Payload contains Shipment ID, Origin, Destination, and Transfer ID.
  - **Storage Bin Labels:** Payload contains Warehouse ID, Zone, Rack, and Shelf ID.
- **FR-QR-02 [MUST]:** The web dashboard must provide an integrated camera-based QR scanner (using HTML5/WebRTC video stream) allowing warehouse and store clerks to scan items directly on tablets/desktops without proprietary hardware.
- **FR-QR-03 [MUST]:** The scanner interface must support hardware USB/Bluetooth barcode wedge scanners (keyboard emulation mode with `Enter` delimiter).

### 8.8 Operational Alerts & Notifications
- **FR-NOTIF-01 [MUST]:** System must generate high-priority in-app operational alerts when:
  - Any medicine balance at Central Warehouse or Store drops below configured `min_stock_level`.
  - Any batch approaches expiry within $\le 30$ days (Critical) or $\le 90$ days (Warning).
  - A shipment status is updated to `DELAYED` or exceeds its scheduled ETA without arrival.
  - A transfer arrival is recorded with a quantity discrepancy.
- **FR-NOTIF-02 [SHOULD]:** Deliver email notifications via SMTP for daily digest of expiring stock and urgent stockout alerts.

---

## 9. Non-Functional Requirements (NFRs)

### 9.1 Security & Compliance
- **NFR-SEC-01:** All communications must be encrypted using **TLS 1.3** in transit.
- **NFR-SEC-02:** User passwords must be salted and hashed using **BCrypt** (work factor 12) or **Argon2id**.
- **NFR-SEC-03:** Authentication via stateless **JWT** (JSON Web Tokens) with a short validity lifespan (15 minutes) coupled with secure, sliding **Refresh Tokens** (7-day lifespan).
- **NFR-SEC-04:** Server-side authorization must be enforced on every endpoint using role and permission checks (`@PreAuthorize`).
- **NFR-SEC-05:** Security headers must be enforced: `Content-Security-Policy`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Strict-Transport-Security`.
- **NFR-SEC-06:** Zero secret leakage in source control; all sensitive credentials, database keys, and JWT secrets must be loaded via runtime environment variables.

### 9.2 Data Integrity & Concurrency
- **NFR-INT-01:** Optimistic locking (`@Version`) must be enforced on all stock balance records to prevent lost updates during concurrent transfer/dispatch requests.
- **NFR-INT-02:** All state-changing inventory transactions must be wrapped in strict database ACID transactions (`@Transactional(isolation = Isolation.READ_COMMITTED)`).
- **NFR-INT-03:** Critical mutation endpoints (e.g., Transfer Creation, Inbound Receipt, Dispense) must require an `X-Idempotency-Key` header to prevent duplicate execution on network retries.
- **NFR-INT-04:** The double-entry ledger tables (`inventory_journal_entries`, `inventory_ledger_lines`) and audit log (`audit_logs`) must be strictly **Append-Only (INSERT only)**; `UPDATE` and `DELETE` queries are prohibited at both application and database permission levels.

### 9.3 Performance & Scalability
- **NFR-PERF-01:** Read API endpoints (catalog, stock balances, shipment status) must respond with **P95 Latency < 150ms** under nominal load.
- **NFR-PERF-02:** Complex transactional write operations (Inbound receipt, FEFO allocation, dispatch) must respond with **P95 Latency < 350ms**.
- **NFR-PERF-03:** Database queries on all list endpoints must enforce server-side pagination with default page size of 20 and maximum page size of 100.
- **NFR-PERF-04:** The database schema must support at least 1,000,000 inventory transaction records and 100,000 active batches with zero degradation in index lookup speeds.

### 9.4 Usability & Accessibility
- **NFR-ACC-01:** The web interface must comply with **WCAG 2.1 Level AA** accessibility guidelines, including minimum 4.5:1 text contrast ratios.
- **NFR-ACC-02:** Statuses must never rely solely on color; every status badge must display an explicit text label and distinctive icon.
- **NFR-ACC-03:** All primary data tables and modal forms must support full keyboard navigation (Tab order, Arrow keys, Enter to submit, Escape to dismiss).
- **NFR-ACC-04:** Layout must be desktop-first (optimized for 1440px / 1080p monitors) but fully responsive down to 768px tablet viewports for warehouse floor tablets.

---

## 10. Scope Matrix (MVP vs. Post-MVP vs. Future)

| Feature Area | MVP (Phase 0 – 10) | Post-MVP (Phase 11) | Future / Advanced (Phase 12+) |
| :--- | :--- | :--- | :--- |
| **Authentication & RBAC** | JWT + Refresh Tokens, 5 fixed roles, Permission Matrix | MFA / TOTP Login, Session Management Dashboard | SSO / SAML / OAuth2 Enterprise Integration |
| **Medicine Catalog** | Full CRUD, Categories, Units, Storage conditions | Bulk CSV import/export of medicines | External FDA/RxNorm drug database sync |
| **Batch & Expiry** | Batch tracking, 30/60/90-day classification, FEFO algorithm | Batch quarantine & recall workflows | Barcode GS1-128 parsing with AI vision |
| **Inventory Ledger** | Multi-location (Central + Stores), Double-entry ledger, `@Version` lock | Stock adjustment approvals workflow | Predictive demand forecasting |
| **Transfers & Dispatch** | End-to-end transfer lifecycle, auto-reservation, pick lists | Multi-batch split transfers | Automated inter-store stock balancing |
| **Shipments & Transit** | Shipment records, milestone log, Mock carrier adapter | Interactive Leaflet map with route polyline | Live GPS telemetry & AfterShip carrier API |
| **Barcode / QR** | QR/Barcode SVG/PNG generation, HTML5 Camera scanner | Zebra thermal printer ZPL output | Bluetooth BLE wearable scanner support |
| **Alerts & Reports** | In-app notification center, Inventory valuation, Expiry risk | SMTP Email digests, PDF transfer manifests | Scheduled automated report webhooks |

---

## 11. Acceptance Criteria & Testable Scenarios

### Scenario AC-01: Inbound Batch Invariant Enforcement
- **Given** an authenticated `CENTRAL_WAREHOUSE_MANAGER` receiving a consignment for a medicine with `min_receiving_shelf_life_days = 90`,
- **When** attempting to record an inbound batch with an Expiry Date that is inside the minimum shelf-life window (e.g. only 45 days remaining from receipt date),
- **Then** the API must reject the request with HTTP `422 Unprocessable Entity` and ProblemDetail detail citing `"Batch expiry date fails minimum receiving shelf-life requirement (90 days required, 45 days remaining)."`

### Scenario AC-02: FEFO Allocation Priority
- **Given** Central Warehouse has:
  - Batch A: 100 units, Expiry `2026-11-01`
  - Batch B: 150 units, Expiry `2026-09-15`
  - Batch C: 200 units, Expiry `2027-01-10`
- **When** a store submits an approved transfer request for 200 units,
- **Then** the FEFO algorithm must allocate:
  1. 150 units from Batch B (earliest expiry),
  2. 50 units from Batch A (next earliest expiry),
  3. 0 units from Batch C.
- **And** Batch B and Batch A stock balances in Central Warehouse must transition to `reserved_quantity += allocated_amount`.

### Scenario AC-03: Negative Inventory Prevention
- **Given** Store A has available quantity of 10 units for Batch X,
- **When** two concurrent dispensing requests of 8 units each arrive simultaneously,
- **Then** exactly one request must succeed, and the second request must fail with HTTP `409 Conflict` or HTTP `422 Unprocessable Entity` citing `"Insufficient available stock"`, ensuring stock never drops below 0.

### Scenario AC-04: Duplicate Shipment Receiving Protection
- **Given** a shipment `SHP-001` has already been marked as `DELIVERED` and its items received into Store B,
- **When** an operator attempts to call the receive endpoint on `SHP-001` again,
- **Then** the system must reject the request with HTTP `409 Conflict` with code `"SHIPMENT_ALREADY_RECEIVED"` and make zero ledger adjustments.

---

## 12. Open Questions & Documented Assumptions

1. **Assumption on Multi-Tenancy:** MedTrack is designed as a dedicated single-tenant system for a regional healthcare provider / warehouse network. Multi-tenant organization isolation is not required for MVP.
2. **Assumption on Carrier API Integration:** Third-party carrier APIs (e.g., FedEx, DHL, AfterShip) have rate limits and varying webhook schemas. For MVP, MedTrack implements a robust `ShipmentTrackingProvider` interface with a full-featured internal tracking engine and mock provider, allowing drop-in commercial adapters in Post-MVP without altering domain logic.
3. **Assumption on Barcode Standards:** Medicine manufacturers use both linear Code-128 and 2D DataMatrix/QR codes. The system generates high-density QR codes containing compact JSON payloads (`{"sku":"...","bat":"...","exp":"..."}`) while supporting standard 1D SKU scanning.
