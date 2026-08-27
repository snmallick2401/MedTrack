<div align="center">

# 💊 MedTrack

### Enterprise Pharmaceutical Inventory, Transportation & Cold-Chain Tracking Platform

[![Java 25 LTS](https://img.shields.io/badge/Java-25%20LTS-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot 4.1.1](https://img.shields.io/badge/Spring%20Boot-4.1.1-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL 18.6](https://img.shields.io/badge/PostgreSQL-18.6-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![React 19](https://img.shields.io/badge/React-19.0.0-61DAFB?style=for-the-badge&logo=react&logoColor=black)](https://react.dev/)
[![TypeScript 5.9](https://img.shields.io/badge/TypeScript-5.9-3178C6?style=for-the-badge&logo=typescript&logoColor=white)](https://www.typescriptlang.org/)
[![Vite 6](https://img.shields.io/badge/Vite-6.4.3-646CFF?style=for-the-badge&logo=vite&logoColor=white)](https://vitejs.dev/)
[![Tailwind CSS 3.4](https://img.shields.io/badge/Tailwind%20CSS-3.4-06B6D4?style=for-the-badge&logo=tailwindcss&logoColor=white)](https://tailwindcss.com/)
[![Playwright 1.58](https://img.shields.io/badge/Playwright-1.58-2EAD33?style=for-the-badge&logo=playwright&logoColor=white)](https://playwright.dev/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](https://opensource.org/licenses/MIT)

<p align="center">
  <b>Enterprise-grade, GxP-compliant pharmaceutical inventory management and inter-facility logistics.</b><br/>
  Featuring double-entry ledger accounting, automated FEFO allocation, minimum shelf-life enforcement, 2D QR / Code-128 labeling, in-browser optical scanning, real-time transportation telemetry, and cryptographic audit trails.
</p>

[Explore Documentation](docs/prd.md) • [View Architecture](docs/architecture.md) • [API Contract](docs/api/openapi.yaml) • [Getting Started](#-getting-started)

</div>

---

## 📑 Table of Contents

- [Overview](#-overview)
- [Key Architectural Capabilities](#-key-architectural-capabilities)
- [End-to-End Pharmaceutical Lifecycle](#-end-to-end-pharmaceutical-lifecycle)
- [Double-Entry Ledger Mechanics](#-double-entry-ledger-mechanics)
- [Technology Baseline](#-technology-baseline)
- [System Architecture](#-system-architecture)
- [Repository Structure](#-repository-structure)
- [Getting Started](#-getting-started)
  - [Prerequisites](#prerequisites)
  - [1. Database Configuration](#1-database-configuration)
  - [2. Backend Setup](#2-backend-setup)
  - [3. Frontend Setup](#3-frontend-setup)
  - [4. Docker Compose Deployment](#4-docker-compose-deployment)
- [Pre-Seeded Roles & Personas](#-pre-seeded-roles--personas)
- [Testing & Quality Assurance](#-testing--quality-assurance)
  - [Automated Backend Suite](#automated-backend-suite)
  - [Frontend Component Tests](#frontend-component-tests)
  - [Playwright End-to-End Suite](#playwright-end-to-end-suite)
- [REST API & OpenAPI Specification](#-rest-api--openapi-specification)
- [UI/UX, Accessibility & Design Tokens](#-uiux-accessibility--design-tokens)
- [Environment Variables Reference](#-environment-variables-reference)
- [Security & Regulatory Compliance](#-security--regulatory-compliance)
- [License & Support](#-license--support)

---

## 🌟 Overview

**MedTrack** is an enterprise-tier operational management platform engineered specifically for healthcare systems, regional pharmaceutical repositories, and multi-depot distribution networks. It strictly enforces pharmaceutical supply chain integrity (GxP / FDA 21 CFR Part 11) across procurement, storage bin allocation, order fulfillment, and inter-facility transit.

### Why Standard ERP / Inventory Systems Fail for Pharmaceuticals:
* **Negative Stock Balances**: Race conditions in high-throughput distribution centers create phantom inventory.
* **Expired Medication Dispensation**: Inability to enforce atomic First-Expired, First-Out (FEFO) rules leading to clinical non-compliance and financial waste.
* **Substandard Inbound Receiving**: Lack of automated minimum shelf-life validation at the dock door.
* **Untracked Cold-Chain & In-Transit Delays**: Zero real-time telemetry or milestone delay alerting during transportation.
* **Audit Trail Tampering**: Mutable audit records that fail stringent regulatory inspections.

MedTrack eliminates these vulnerabilities through mathematical ledger invariants, automated FEFO reservation engines, optical barcode tooling, and immutable cryptographic audit logging.

---

## 🚀 Key Architectural Capabilities

```text
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                   CORE ARCHITECTURAL PILLARS                                    │
├────────────────────────────────┬────────────────────────────────┬──────────────────────────────┤
│ 1. True Double-Entry Ledger    │ 2. Enforced FEFO Allocation    │ 3. Explicit Domain Triad     │
│ • Balanced Debit & Credit lines│ • First-Expired, First-Out     │ • Transfer (Business Intent) │
│ • 4-Bucket state tracking      │ • Strict manual override audit │ • Shipment (Transport)       │
│   (Avail/Reserv/Quaran/Transit)│ • Configurable shelf-life days │ • Tracking (Telemetry)       │
├────────────────────────────────┼────────────────────────────────┼──────────────────────────────┤
│ 4. Single-Use Token RTR        │ 5. Optical & Barcode Labels    │ 6. Cryptographic Audit Log   │
│ • Refresh Token Rotation (RTR) │ • 2D QR (GS1 digital payload)  │ • Synchronous commit         │
│ • Token family replay defense  │ • 1D Code-128 barcode labels   │ • SHA-256 hash chaining      │
│ • HttpOnly SameSite=Strict     │ • In-browser webcam scanner    │ • Non-repudiation guarantee  │
└────────────────────────────────┴────────────────────────────────┴──────────────────────────────┘
```

* **Mathematical Inventory Invariants**:
  Physical inventory counts are computed exclusively by aggregating immutable journal ledger transactions. Negative inventory balances are strictly impossible at the database constraint level.
* **Automated FEFO (First-Expired, First-Out) Engine**:
  When a regional clinic or store requests stock, MedTrack searches active inventory across storage bins and atomically locks the earliest-expiring batch matching the requested dosage formulation.
* **Inbound Shelf-Life Gatekeeping (AC-01)**:
  Rejects any inbound batch with remaining shelf-life below 90 days (configurable) with an instant RFC 7807 `422 Unprocessable Entity` response.
* **Idempotency & Replay Protection**:
  All financial and operational endpoints (`POST /stock-transfers`, `/allocate`, `/dispatch`, `/receive`) accept an `Idempotency-Key` header with SHA-256 payload verification.
* **Dual Alert & Telemetry Engine**:
  * **Critical Expiry Alarm ($\le 30$ Days)**: High-priority flags and red indicators for immediate quarantine.
  * **Near Expiry Alarm ($31 - 90$ Days)**: Actionable notifications for priority routing or redistribution.
  * **Shipment Delay Alarm**: Real-time telemetry monitoring estimated vs. actual milestones, alerting operators if transport is stalled.
* **Integrated Barcode & Optical Scanner**:
  Generates GS1-compatible 2D QR Codes and 1D Code-128 barcodes. Features an in-browser scanner with real-time identifier parsing and batch routing.
* **Enterprise Reporting & Streaming Exports**:
  Authoritative streaming CSV exports for physical balance sheets and near-expiry risk audits.

---

## 🔄 End-to-End Pharmaceutical Lifecycle

MedTrack orchestrates the full pharmaceutical supply chain lifecycle across 7 deterministic stages:

```text
  [ 1. Inbound Receipt ]
         │  (AC-01: Validates ≥ 90 days shelf-life at dock)
         ▼
  [ 2. Storage & 2D QR Labeling ]
         │  (Generates GS1 QR Code & assigns warehouse storage bin)
         ▼
  [ 3. Stock Transfer Request ]
         │  (Store manager initiates inter-depot replenishment)
         ▼
  [ 4. Automated FEFO Allocation ]
         │  (System locks earliest-expiring viable stock batch)
         ▼
  [ 5. Pick, Pack & Manifest ]
         │  (Warehouse manager confirms physical pick and seals container)
         ▼
  [ 6. Dispatch & Telemetry ]
         │  (Debits Available -> Credits In-Transit; GPS tracking active)
         ▼
  [ 7. Destination Receipt ]
            (Receiving store validates manifest -> Credits local balance)
```

---

## 📊 Double-Entry Ledger Mechanics

In MedTrack, inventory balances are never modified by direct arithmetic increments (`UPDATE inventory SET qty = qty + 10`). Instead, MedTrack models physical inventory using **financial double-entry accounting**:

$$\text{Physical Inventory Balance} = \sum \text{Debits} - \sum \text{Credits}$$

### Inventory Account Classification:

| Account Type | Description | Normal Balance |
| :--- | :--- | :--- |
| `AVAILABLE` | Unreserved stock in storage bins ready for allocation | **Debit** |
| `RESERVED` | Stock earmarked for an approved transfer order | **Debit** |
| `QUARANTINED`| Stock suspended due to quality holds or critical expiry ($\le 30\text{d}$) | **Debit** |
| `IN_TRANSIT` | Dispatched stock currently aboard transport vehicles | **Debit** |
| `SUPPLIER` | External supplier source account | **Credit** |
| `DISPENSATION`| Consumed or clinical issue offset account | **Credit** |

Every inventory operation creates an immutable `InventoryJournalEntry` with corresponding balanced `InventoryLedgerLine` records.

---

## 💻 Technology Baseline

### Backend Architecture
| Component | Technology / Version | Description |
| :--- | :--- | :--- |
| **Runtime** | Java 25 LTS | Virtual threads, record patterns, modern language runtime |
| **Framework** | Spring Boot 4.1.1 | Reactive core, web MVC, caching, scheduler engine |
| **Security** | Spring Security 7.x | Stateless JWT authentication, Refresh Token Rotation (RTR) |
| **ORM / Persistence**| Hibernate ORM 7.4.5 | JPA specification, optimistic locking, batch operations |
| **Database** | PostgreSQL 18.6 | ACID transactions, JSONB, native UUIDs, spatial indices |
| **Database Migrations**| Flyway (V1 → V7) | Automated, versioned, zero-drift schema migrations |
| **Barcode Engine** | ZXing 3.5.3 | High-density 2D QR Code & 1D Code-128 rendering |
| **API Contract** | OpenAPI 3.1 & Swagger UI | Interactive API documentation and RFC 7807 problem details |

### Frontend Architecture
| Component | Technology / Version | Description |
| :--- | :--- | :--- |
| **Framework** | React 19.0.0 | Concurrent rendering, modern hooks, functional architecture |
| **Language** | TypeScript 5.9.x | Strict type safety, shared DTO interfaces, zero `any` |
| **Build Tool** | Vite 6.4.3 | Instant HMR, tree-shaking, optimized production bundles |
| **Styling** | Tailwind CSS 3.4.x | Token-based theming, WCAG AA contrast, Light/Dark modes |
| **State & Data Fetching** | TanStack Query v5 & Zustand 5 | Client cache invalidation, asynchronous mutation pipelines |
| **Router** | React Router v7 | Nested route layout, exact active state matching, RBAC gates |
| **Icons** | Lucide React | Consistent accessible vector iconography |
| **E2E Testing** | Playwright 1.58.x | Multi-browser headless automation, visual regression |

---

## 🏛️ System Architecture

```mermaid
graph TD
    subgraph Frontend ["Frontend (React 19 + TypeScript + Vite 6)"]
        UI[App Shell & Command Palette]
        AuthUI[Auth & RBAC Guards]
        InvUI[Inbound Receiving & Stock View]
        TrfUI[Stock Transfers & FEFO Workbench]
        ScanUI[2D QR / Code-128 Scanner]
        RepUI[Near-Expiry & CSV Reports]
    end

    subgraph Backend ["Backend (Spring Boot 4.1.1 - Java 25 LTS)"]
        API[Spring MVC REST API Controller]
        Sec[Spring Security 7 & JWT Filter]
        Idem[Idempotency Engine & Hash Verifier]
        Ledger[Double-Entry Inventory Ledger Service]
        FEFO[FEFO Allocation & Reservation Engine]
        Ship[Shipment & Telemetry Engine]
        Alert[Automated Expiry & Delay Worker]
        Audit[Cryptographic Audit Trail Engine]
    end

    subgraph Storage ["Database (PostgreSQL 18.6)"]
        Flyway[Flyway Schema V1-V7]
        DB_Ledger[(Inventory Journals & Balances)]
        DB_Master[(Medicines, Batches, Warehouses)]
        DB_Transfers[(Stock Transfers & Shipments)]
        DB_Audit[(Immutable Audit Logs)]
    end

    UI --> Sec
    Sec --> API
    API --> Idem
    Idem --> Ledger
    Idem --> FEFO
    Idem --> Ship
    Alert --> Ledger
    Ledger --> DB_Ledger
    FEFO --> DB_Ledger
    Ship --> DB_Transfers
    Audit --> DB_Audit
```

---

## 🗂️ Repository Structure

```text
MedTrack/
├── backend/                              # Spring Boot 4.1.1 REST API
│   ├── src/main/java/com/medtrack/
│   │   ├── audit/                        # Cryptographic audit logging & hash verification
│   │   ├── auth/                         # JWT authentication, user credentials, RBAC
│   │   ├── barcode/                      # 2D QR & 1D Code-128 barcode generation service
│   │   ├── common/                       # ProblemDetail RFC 7807, exceptions, pagination
│   │   ├── config/                       # SecurityConfig, OpenAPI, Jackson, Async config
│   │   ├── idempotency/                  # Idempotency token storage & payload hashing
│   │   ├── inventory/                    # Double-entry ledger, balances, inbound receiving
│   │   ├── masterdata/                   # Medicines, batches, warehouses, storage bins
│   │   ├── notification/                 # Expiry & shipment delay alert engine
│   │   ├── report/                       # CSV streaming exporters & expiry risk queries
│   │   ├── tracking/                     # Transportation telemetry, milestones & geocoding
│   │   └── transfer/                     # Inter-warehouse stock transfers & FEFO allocation
│   ├── src/main/resources/
│   │   ├── db/migration/                 # Flyway migrations V1 to V7
│   │   └── application.properties        # Database, JWT, and server configurations
│   ├── pom.xml                           # Maven dependencies & build plugins
│   └── Dockerfile
│
├── frontend/                             # React 19 + TypeScript SPA
│   ├── src/
│   │   ├── components/                   # AppShell, CommandPalette, StatusBadge, States
│   │   ├── features/                     # Feature modules (auth, dashboard, inventory,
│   │   │                                 # master-data, operations, reports, scanner)
│   │   ├── services/                     # Typed API clients (Axios, TanStack Query)
│   │   ├── styles/                       # Design tokens, CSS custom properties, Tailwind
│   │   └── types/                        # Shared TypeScript interfaces & DTO definitions
│   ├── e2e/                              # Playwright End-to-End Test Suite (15 specs)
│   ├── index.html
│   ├── package.json
│   ├── tsconfig.json
│   └── vite.config.ts
│
├── docs/                                 # Full Architectural Documentation
│   ├── prd.md                            # Product Requirements Document
│   ├── architecture.md                   # System Architecture & C4 Models
│   ├── design.md                         # Design System & Token Specifications
│   ├── rules.md                          # Engineering Invariants & Coding Rules
│   └── phases.md                         # Implementation Phase Breakdown
│
└── README.md                             # Project Documentation & Getting Started
```

---

## 🚦 Getting Started

### Prerequisites

* **Java Development Kit (JDK)**: `Java 25 LTS` (e.g. `C:\Dev\Tools\jdk-25`)
* **Apache Maven**: `3.9.x` or higher
* **PostgreSQL**: `18.6` (e.g. `C:\Dev\Tools\pgsql-18.6`) running on port `5432`
* **Node.js**: `v20.x` or `v22.x` (LTS)
* **npm**: `v10.x` or higher

---

### 1. Database Configuration

Create the PostgreSQL database and user:

```sql
CREATE USER medtrack WITH PASSWORD 'medtrack';
CREATE DATABASE medtrack OWNER medtrack;
GRANT ALL PRIVILEGES ON DATABASE medtrack TO medtrack;
```

---

### 2. Backend Setup

1. Navigate to the backend directory:
   ```powershell
   cd backend
   ```
2. Build and run database migrations:
   ```powershell
   mvn clean package -DskipTests
   ```
3. Start the Spring Boot application:
   ```powershell
   java -jar target/medtrack-backend-0.0.1-SNAPSHOT.jar
   ```
   *The backend will start at `http://localhost:8080` and automatically apply Flyway migrations V1–V7.*

---

### 3. Frontend Setup

1. Navigate to the frontend directory:
   ```powershell
   cd frontend
   ```
2. Install npm dependencies:
   ```powershell
   npm install
   ```
3. Start the Vite development server:
   ```powershell
   npm run dev
   ```
   *The frontend application will be live at `http://localhost:5173`.*

---

### 4. Docker Compose Deployment

To launch the complete MedTrack stack in isolated Docker containers:

```bash
docker compose -f infra/docker-compose.yml up --build -d
```

---

## 👥 Pre-Seeded Roles & Personas

MedTrack includes 5 pre-seeded role accounts for development, testing, and compliance demonstration:

| Email Address | Default Password | Role | Operational Responsibilities |
| :--- | :--- | :--- | :--- |
| `admin@medtrack.local` | `ChangeMe123!` | `SUPER_ADMIN` | Global master data, warehouse setup, user management, audit review |
| `warehouse@medtrack.local` | `ChangeMe123!` | `CENTRAL_WAREHOUSE_MANAGER` | Inbound receiving, storage bin mapping, FEFO pick/pack, dispatch |
| `store@medtrack.local` | `ChangeMe123!` | `STORE_MANAGER` | Stock transfer requests, inbound shipment receipt, inventory review |
| `logistics@medtrack.local` | `ChangeMe123!` | `LOGISTICS_COORDINATOR` | Dispatch milestone telemetry, delay resolution, transport tracking |
| `auditor@medtrack.local` | `ChangeMe123!` | `AUDITOR` | Read-only compliance inspection, cryptographic audit log review |

---

## 🧪 Testing & Quality Assurance

MedTrack enforces continuous test verification across every architectural tier:

### Automated Backend Suite
Executes Spring Boot integration tests, Flyway migrations, double-entry ledger calculations, and security authorization checks:
```powershell
cd backend
mvn clean test
```
*Result: 14/14 tests passing (0 failures).*

---

### Frontend Component Tests
Executes Vitest and React Testing Library unit/component specifications:
```powershell
cd frontend
npm test
```
*Result: 10/10 tests passing (0 failures).*

---

### Playwright End-to-End Suite
Executes 15 comprehensive browser-driven end-to-end tests against the live PostgreSQL backend:
```powershell
cd frontend
npx playwright test
```

```text
Running 15 tests using 1 worker:

  ✅ [1]  e2e/acceptance-criteria.spec.ts  › AC-01: Inbound receiving rejects batch (<90 days shelf-life) [422]
  ✅ [2]  e2e/acceptance-criteria.spec.ts  › AC-04: Re-receiving completed shipment is prevented [409]
  ✅ [3]  e2e/accessibility-responsive.ts  › Dark mode token toggle & localStorage persistence
  ✅ [4]  e2e/accessibility-responsive.ts  › Command palette (Ctrl+K / ⌘K) keyboard navigation & execution
  ✅ [5]  e2e/accessibility-responsive.ts  › Command palette auto-scrolls listbox with arrow keys (Down & Up)
  ✅ [6]  e2e/accessibility-responsive.ts  › Mobile responsive drawer & backdrop overlay (375x812)
  ✅ [7]  e2e/accessibility-responsive.ts  › Tablet responsive viewport (768x1024)
  ✅ [8]  e2e/auth.spec.ts                 › Validation error on invalid credentials
  ✅ [9]  e2e/auth.spec.ts                 › Sign in as SUPER_ADMIN and sign out
  ✅ [10] e2e/medtrack.spec.ts             › 18-step primary pharmaceutical business journey (PostgreSQL 18.6)
  ✅ [11] e2e/reports-and-labels.spec.ts   › Reports near-expiry risk table & CSV exports
  ✅ [12] e2e/reports-and-labels.spec.ts   › 2D QR and 1D Code 128 barcode labels
  ✅ [13] e2e/reports-and-labels.spec.ts   › Barcode scanner interface & ID resolution
  ✅ [14] e2e/reports-and-labels.spec.ts   › Notification evaluation and alerts
  ✅ [15] e2e/roles-and-permissions.spec.ts › RBAC validation across core functional routes

  15 passed (25.8s)
```

---

## 📖 REST API & OpenAPI Specification

MedTrack exposes a fully documented, RFC 7807 compliant RESTful API:

* **Interactive Swagger UI**: `http://localhost:8080/swagger-ui.html`
* **Raw OpenAPI 3.1 YAML**: Located in [`docs/api/openapi.yaml`](docs/api/openapi.yaml)

### Key Endpoint Groups

| Method | Path | Summary | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/auth/login` | Authenticate | Returns Access JWT and sets HttpOnly Refresh Token |
| `POST` | `/api/v1/inbound-receipts` | Receive Batch | Atomically books batch stock into storage bin (AC-01) |
| `POST` | `/api/v1/stock-transfers` | Request Transfer | Creates inter-warehouse transfer intent |
| `POST` | `/api/v1/stock-transfers/{id}/allocate` | FEFO Allocation | Automatically reserves earliest-expiring inventory |
| `POST` | `/api/v1/shipments` | Create Shipment | Binds allocated batches to transport manifest |
| `POST` | `/api/v1/shipments/{id}/dispatch` | Dispatch | Debits source warehouse; transitions stock to In Transit |
| `POST` | `/api/v1/shipments/{id}/receive` | Receive Stock | Credits destination warehouse; finalizes transfer |
| `GET` | `/api/v1/barcodes/qr/{batchId}` | Generate QR Code | GS1 2D QR Code image with dosage & shelf-life metadata |
| `GET` | `/api/v1/reports/inventory` | Inventory CSV | Streams real-time physical balance ledger in CSV format |
| `GET` | `/api/v1/reports/expiry/data` | Near Expiry Risk | Returns batches nearing expiry window ($30\text{d} / 90\text{d}$) |

---

## 🎨 UI/UX, Accessibility & Design Tokens

* **WCAG 2.1 AA Compliance**: All typography, badges, inputs, and interactive buttons exceed the 4.5:1 contrast ratio threshold in both Light and Dark modes.
* **Command Palette (`Ctrl+K` / `⌘K`)**: Quick navigation, action execution, and CSV exports with auto-scrolling keyboard listbox navigation.
* **Unified Segmented Controls**: Visual baseline alignment, distinct active states, and padding across all operational toolbars.
* **Responsive Layouts**: Designed on a 4px geometric grid with dedicated desktop, tablet (`768px`), and mobile (`375px`) responsive breakpoints.

---

## ⚙️ Environment Variables Reference

| Variable | Default Value | Description |
| :--- | :--- | :--- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/medtrack` | PostgreSQL JDBC connection string |
| `SPRING_DATASOURCE_USERNAME` | `medtrack` | PostgreSQL database user |
| `SPRING_DATASOURCE_PASSWORD` | `medtrack` | PostgreSQL database password |
| `MEDTRACK_JWT_SECRET` | `404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970` | 256-bit secret key for HMAC-SHA signing |
| `MEDTRACK_JWT_EXPIRATION_MS` | `900000` (15 minutes) | JWT Access Token time-to-live |
| `VITE_API_BASE_URL` | `http://localhost:8080/api/v1` | Backend API URL for Vite frontend |

---

## 🔒 Security & Regulatory Compliance

* **Stateless Token RTR**: JWT access tokens have a short lifespan (15 minutes) paired with Single-Use Refresh Token Rotation (RTR). Replaying an old refresh token automatically invalidates the entire token family.
* **Cryptographic Non-Repudiation**: Sensitive journal entries and audit trails generate immutable SHA-256 integrity checksums.
* **Zero Negative Balance Invariant**: Guaranteed through atomic database constraints and double-entry debit/credit verification.
* **Idempotency Locking**: Protects all mutation endpoints against concurrent duplicate execution.

---

## 📄 License & Support

MedTrack is released under the [MIT License](LICENSE).

For technical questions, bug reports, or architecture inquiries, please open an issue in this repository.

---

<div align="center">
  <sub>Built with ❤️ for resilient healthcare logistics and pharmaceutical inventory integrity.</sub>
</div>