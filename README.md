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

<p align="center">
  <b>Enterprise-grade, GxP-compliant pharmaceutical inventory management and inter-facility logistics.</b><br/>
  Featuring double-entry ledger accounting, automated FEFO allocation, minimum shelf-life enforcement, 2D QR / Code-128 labeling, in-browser optical scanning, real-time transportation telemetry, and cryptographic audit trails.
</p>

</div>

---

## 📑 Table of Contents

- [Overview](#-overview)
- [Key Architectural Capabilities](#-key-architectural-capabilities)
- [Technology Baseline](#-technology-baseline)
- [System Architecture](#-system-architecture)
- [Repository Structure](#-repository-structure)
- [Getting Started](#-getting-started)
  - [Prerequisites](#prerequisites)
  - [1. Database Configuration](#1-database-configuration)
  - [2. Backend Setup](#2-backend-setup)
  - [3. Frontend Setup](#3-frontend-setup)
- [Pre-Seeded Roles & Personas](#-pre-seeded-roles--personas)
- [Testing & Quality Assurance](#-testing--quality-assurance)
  - [Automated Backend Suite](#automated-backend-suite)
  - [Frontend Component Tests](#frontend-component-tests)
  - [Playwright End-to-End Suite](#playwright-end-to-end-suite)
- [REST API & Specifications](#-rest-api--specifications)
- [UI/UX & Design System](#-uiux--design-system)
- [Security & Compliance](#-security--compliance)
- [License](#-license)

---

## 🌟 Overview

**MedTrack** is designed for hospital networks, central pharmaceutical repositories, and regional healthcare supply chains. It enforces pharmaceutical compliance (GxP/FDA 21 CFR Part 11) across procurement, warehouse storage, pick-pack fulfillment, and inter-facility transit.

Traditional inventory management systems frequently fail in pharmaceutical environments due to negative balance race conditions, lack of strict shelf-life validation, or unverified manual lot selection. MedTrack resolves these challenges through:

1. **Atomic Double-Entry Accounting**: Every physical movement generates balanced Debit/Credit journal ledger entries.
2. **Automated FEFO (First-Expired, First-Out)**: Allocation algorithms strictly reserve the earliest-expiring viable stock batches.
3. **90-Day Inbound Policy (AC-01)**: Rejects supplier shipments with insufficient remaining shelf-life at the receiving dock.
4. **Idempotent Mutations**: Guarantees zero double-allocation or double-dispatch during network retries.
5. **Real-Time GPS & Milestone Telemetry**: Tracks shipments in transit with automated delay alarms.

---

## 🚀 Key Architectural Capabilities

```text
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                   CORE ARCHITECTURAL PILLARS                                    │
├────────────────────────────────┬────────────────────────────────┬──────────────────────────────┤
│ 1. True Double-Entry Ledger    │ 2. Enforced FEFO Allocation    │ 3. Explicit Domain Triad     │
│ • Balanced Debit & Credit lines│ • First-Expired, First-Out     │ • Transfer (Business Intent) │
│ • 3-Bucket state tracking      │ • Strict manual override audit │ • Shipment (Transport)       │
│   (Available/Reserved/Quarant) │ • Configurable shelf-life days │ • Tracking (Telemetry)       │
├────────────────────────────────┼────────────────────────────────┼──────────────────────────────┤
│ 4. Single-Use Token RTR        │ 5. Optical & Barcode Labels    │ 6. Cryptographic Audit Log   │
│ • Refresh Token Rotation (RTR) │ • 2D QR (GS1 digital payload)  │ • Synchronous commit         │
│ • Token family replay defense  │ • 1D Code-128 barcode labels   │ • SHA-256 hash chaining      │
│ • HttpOnly SameSite=Strict     │ • In-browser webcam scanner    │ • Non-repudiation guarantee  │
└────────────────────────────────┴────────────────────────────────┴──────────────────────────────┘
```

* **Double-Entry Journal Balancing**:
  Physical inventory counts are computed exclusively by aggregating immutable journal ledger transactions. Negative inventory balances are strictly impossible.
* **FEFO Stock Allocation Engine**:
  When a distribution store requests stock, MedTrack searches active inventory across storage bins and automatically locks the earliest-expiring batch matching the requested dosage formulation.
* **Idempotency & Replay Protection**:
  All financial and operational endpoints (`POST /stock-transfers`, `/allocate`, `/dispatch`, `/receive`) accept an `Idempotency-Key` header with SHA-256 payload verification.
* **Automated Expiry & Delay Alarm Engine**:
  * **Critical Expiry Alarm ($\le 30$ Days)**: Immediate high-priority quarantine flags.
  * **Near Expiry Alarm ($31 - 90$ Days)**: Actionable alerts for stock movement or clinical priority.
  * **Shipment Delay Alarm**: Telemetry monitors estimated vs actual milestones and alerts operators if transport is stalled.
* **Barcode & Optical Scanner Interface**:
  Generates GS1-compatible 2D QR Codes and 1D Code-128 barcodes. Features an in-browser scanner with real-time identifier parsing and batch routing.
* **Enterprise Reporting & Data Exports**:
  Authoritative streaming CSV exports for physical balance sheets and near-expiry risk audits.

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
    subgraph Frontend ["Frontend (React 19 + TypeScript + Vite)"]
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

* **Java Development Kit (JDK)**: `Java 25 LTS` (`C:\Dev\Tools\jdk-25`)
* **Apache Maven**: `3.9.x` or higher
* **PostgreSQL**: `18.6` (`C:\Dev\Tools\pgsql-18.6`) running on port `5432`
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

## 📖 REST API & Specifications

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

## 🎨 UI/UX & Design System

* **WCAG 2.1 AA Compliance**: All typography, badges, inputs, and interactive buttons exceed the 4.5:1 contrast ratio threshold in both Light and Dark modes.
* **Command Palette (`Ctrl+K` / `⌘K`)**: Quick navigation, action execution, and CSV exports with auto-scrolling keyboard listbox navigation.
* **Unified Segmented Controls**: Visual baseline alignment, distinct active states, and padding across all operational toolbars.
* **Responsive Layouts**: Designed on a 4px geometric grid with dedicated desktop, tablet (`768px`), and mobile (`375px`) responsive breakpoints.

---

## 🔒 Security & Compliance

* **Stateless Token RTR**: JWT access tokens have a short lifespan (15 minutes) paired with Single-Use Refresh Token Rotation (RTR). Replaying an old refresh token automatically invalidates the entire token family.
* **Cryptographic Non-Repudiation**: Sensitive journal entries and audit trails generate immutable SHA-256 integrity checksums.
* **Zero Negative Balance Invariant**: Guaranteed through atomic database constraints and double-entry debit/credit verification.
* **Idempotency Locking**: Protects all mutation endpoints against concurrent duplicate execution.

---

## 📄 License

MedTrack is licensed under the [MIT License](LICENSE).

---

<div align="center">
  <sub>Built with ❤️ for resilient healthcare logistics and pharmaceutical inventory integrity.</sub>
</div>