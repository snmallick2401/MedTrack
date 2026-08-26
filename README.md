# MedTrack — Medicine Inventory & Transportation Tracking Platform

**MedTrack** is an enterprise-grade operational management system designed to track, manage, and trace medicine inventory across central pharmaceutical warehouses, regional distribution stores, and intermediate transportation routes.

Built with **Spring Boot 4.1.1 (Java 25 LTS)**, **PostgreSQL 18.6**, and a **React 19.2.x / TypeScript 5.9.x / Vite 8.1.x** frontend single-page application.

---

## 📚 Specification & Architecture Source of Truth

The project documentation is structured for human engineers and AI coding agents:

| Document | Purpose | Key Contents |
| :--- | :--- | :--- |
| [**`prd.md`**](file:///c:/Dev/Projects/Java/MedTrack/prd.md) | Product Requirements | Problem statement, user personas, MoSCoW features, and testable acceptance scenarios |
| [**`architecture.md`**](file:///c:/Dev/Projects/Java/MedTrack/architecture.md) | System Architecture | C4 models, ER diagrams, PostgreSQL DDL schemas, double-entry mechanics, sequence diagrams, and security |
| [**`design.md`**](file:///c:/Dev/Projects/Java/MedTrack/design.md) | UI/UX & Design Tokens | 4px spacing grid, CSS custom properties, Tailwind tokens, 8 table states, status badges, and wizard flows |
| [**`rules.md`**](file:///c:/Dev/Projects/Java/MedTrack/rules.md) | AI & Development Constitution | Coding standards, package layering, error protocol (RFC 7807), and zero-negative-inventory invariants |
| [**`phases.md`**](file:///c:/Dev/Projects/Java/MedTrack/phases.md) | Implementation Roadmap | 10 distinct MVP implementation phases with Definition of Done (DoD) criteria |
| [**`docs/api/openapi.yaml`**](file:///c:/Dev/Projects/Java/MedTrack/docs/api/openapi.yaml) | REST API Contract | OpenAPI 3.0 specification with schemas, DTOs, problem details, and authentication security schemes |
| [**`docs/database/erd.md`**](file:///c:/Dev/Projects/Java/MedTrack/docs/database/erd.md) | Database Reference | Physical ERD and table responsibility matrix |
| [**`docs/decisions/`**](file:///c:/Dev/Projects/Java/MedTrack/docs/decisions/ADR-001-double-entry-inventory.md) | Architecture Decision Records | Formal ADRs detailing foundational domain decisions |

---

## 🏛️ Core Domain Principles

```text
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                   CORE ARCHITECTURAL PILLARS                                    │
├────────────────────────────────┬────────────────────────────────┬──────────────────────────────┤
│ 1. True Double-Entry Ledger    │ 2. Enforced FEFO Allocation    │ 3. Explicit Domain Triad     │
│ • Balanced Debit & Credit lines│ • First-Expired, First-Out     │ • Transfer (Business Intent) │
│ • 3-Bucket state tracking      │ • Strict manual override audit │ • Shipment (Transport)       │
│   (Available/Reserved/Quarant) │ • Configurable shelf-life days │ • Tracking (Telemetry)       │
├────────────────────────────────┼────────────────────────────────┼──────────────────────────────┤
│ 4. Single-Use Token RTR        │ 5. First-Class Simulation      │ 6. Synchronous Audit Commit  │
│ • Refresh Token Rotation (RTR) │ • MockTrackingProvider (Dev)   │ • Balance + Ledger + Audit in│
│ • Token family replay defense  │ • MockGeocodingProvider (Dev)  │   one atomic DB transaction  │
│ • HttpOnly SameSite=Strict     │ • Zero external keys for local │ • Post-commit async alerts   │
└────────────────────────────────┴────────────────────────────────┴──────────────────────────────┘
```

---

## 🗂️ Frozen Repository Structure

```text
C:\Dev\Projects\Java\MedTrack\
├── backend/                  # Spring Boot 4.1.1 REST API (Java 25 LTS, Spring Security 7.x, Hibernate ORM 7.4.x, Flyway)
│   ├── src/
│   │   ├── main/java/com/medtrack/
│   │   ├── main/resources/db/migration/
│   │   └── test/
│   ├── pom.xml
│   └── Dockerfile
│
├── frontend/                 # React 19.2.x + TypeScript 5.9.x + Vite 8.1.x SPA (Tailwind CSS 4.x, Leaflet, Zustand)
│   ├── src/
│   │   ├── components/
│   │   ├── features/
│   │   ├── styles/ (tokens.css, globals.css)
│   │   ├── hooks/
│   │   └── services/
│   ├── tailwind.config.js
│   ├── package.json
│   └── Dockerfile
│
├── infra/                    # Deployment & orchestration configurations
│   ├── docker-compose.yml
│   ├── docker-compose.prod.yml
│   └── nginx/
│
├── docs/                     # Full documentation hierarchy
│   ├── prd.md
│   ├── architecture.md
│   ├── design.md
│   ├── rules.md
│   ├── phases.md
│   ├── api/
│   │   └── openapi.yaml
│   ├── database/
│   │   └── erd.md
│   └── decisions/
│       └── ADR-001-double-entry-inventory.md
│
└── README.md
```

---

## 🚀 Recommended Next Step

With all documentation files, database schemas, API specs, and design systems frozen and verified, the next implementation step is:

👉 **Phase 1: Project Setup, Spring Boot Skeleton & PostgreSQL / Flyway Migration Engine**.