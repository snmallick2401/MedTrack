# MedTrack — Engineering & AI Agent Rules (Constitution)

**Document Version:** 1.0.0  
**Status:** Mandatory Standard  
**Target:** All Software Engineers & AI Coding Agents (Codex, Antigravity)  

---

## 1. AI Coding Agent & Codex Operational Directives

### 1.1 Mandatory Pre-Execution Inspection
1. **Inspect Before Modifying:** Agents must read existing entity models, DTOs, services, and tests before making modifications to any file.
2. **Minimal Diff Principle:** Never execute gratuitous refactorings or reformat unrelated files. Make the smallest, most targeted change that completely fulfills the requirement.
3. **No Fabricated APIs or Libraries:** Never assume a library method, API parameter, or database column exists without verifying it in the codebase or official documentation.
4. **No Schema Hallucinations:** Never introduce a new entity field without an accompanying Flyway migration script (`V{num}__{description}.sql`) and an update to `architecture.md`.
5. **No Bypassing Invariants:** Never bypass validation annotations, Spring Security `@PreAuthorize` guards, or domain service checks to "make a test pass".

### 1.2 Boundary of Autonomous AI Changes
- **Permitted Autonomously:** Adding use cases, creating new DTOs, writing unit/integration tests, implementing endpoints within existing domain modules, building UI components adhering to `design.md`.
- **Prohibited Without Explicit User Approval:** Changing database schema structure, modifying authentication token formats, altering the double-entry inventory ledger accounting model, introducing new third-party Maven/NPM dependencies.

---

## 2. Architecture & Domain Boundary Rules

### 2.1 Package & Layering Invariants
1. **Strict Controller Restraint:**
   - Controllers must ONLY accept HTTP requests, trigger validation (`@Valid`), delegate execution to an Application Service / Use Case, and map the domain result to a Response DTO.
   - **Zero Business Logic in Controllers:** No arithmetic, no stock calculations, no direct repository calls, and no conditional state mutation logic.
2. **DTO Layer Isolation:**
   - Entities must never be returned directly from controller methods.
   - Always map Entities to Response DTOs using MapStruct or explicit mapper functions.
   - Request DTOs must be immutable (Java `record` preferred where appropriate).
3. **Cross-Domain Communication:**
   - Direct package-private coupling across domain boundaries is prohibited.
   - Domain modules (e.g., `shipment` interacting with `inventory`) must communicate exclusively through public Service interfaces or application Domain Events.
4. **Explicit Domain Triad Boundaries:**
   - **`transfer`:** Models business movement requests and FEFO stock reservations.
   - **`shipment`:** Models physical transport lifecycle, carrier assignments, and shipping manifests.
   - **`tracking`:** Models in-transit telemetry, GPS coordinates, checkpoint events, and delay exceptions.
   - *Never merge Transfer business state with physical vehicle transit milestones.*
5. **No Circular Package Dependencies:**
   - Package dependencies must flow in a strict acyclic directed graph (DAG). Circular references between services will cause build failure.

---

## 3. REST API Rules & Conventions

### 3.1 Endpoint Naming & HTTP Verbs
- **Base Path:** `/api/v1/`
- **Resource Nouns:** Plural, lowercase, kebab-case (e.g., `/api/v1/stock-transfers`, `/api/v1/storage-locations`).
- **HTTP Methods:**
  - `GET`: Safe, idempotent read operations. Never mutates server state.
  - `POST`: Create a new resource or trigger an explicit domain action (e.g., `POST /api/v1/stock-transfers/{id}/allocate`).
  - `PUT`: Complete idempotent replacement of a mutable resource.
  - `PATCH`: Partial update of specific fields.
  - `DELETE`: Soft delete or deactivation (e.g., marking a user or medicine as `INACTIVE`).

### 3.2 HTTP Status Code Matrix
| Status Code | Meaning | Mandatory Usage in MedTrack |
| :--- | :--- | :--- |
| **`200 OK`** | Success | Standard response for successful `GET`, `PUT`, or non-creation `POST`. |
| **`201 Created`** | Created | Returned by `POST` endpoints creating a new entity (must include `Location` header). |
| **`204 No Content`** | No Content | Returned for successful `DELETE` or state changes returning no body. |
| **`400 Bad Request`** | Bad Syntax | Malformed JSON or unparseable query parameters. |
| **`401 Unauthorized`**| Unauthenticated| Missing, expired, or invalid JWT Bearer token. |
| **`403 Forbidden`** | Unauthorized | Authenticated user lacks required role or permission for the resource. |
| **`404 Not Found`** | Missing Entity | Requested ID does not exist in the database. |
| **`409 Conflict`** | Conflict / Lock | Optimistic lock collision, duplicate receipt, or illegal state transition. |
| **`422 Unprocessable`**| Domain Failure | Valid syntax but violates domain invariants (e.g., Expiry in past, insufficient stock). |
| **`500 Internal Error`**| Server Fault | Unhandled system exceptions (sanitized; no raw stack trace leaked). |

### 3.3 Standard Error Format (RFC 7807)
All error responses must use `application/problem+json` matching the `ProblemDetail` specification:
```json
{
  "type": "https://api.medtrack.internal/errors/{error-code}",
  "title": "{Human Readable Summary}",
  "status": 400,
  "detail": "{Specific explanation of the failure}",
  "instance": "{Request URI}",
  "code": "{ENUM_ERROR_CODE}",
  "timestamp": "2026-08-26T21:10:00Z",
  "invalidParams": [
    {
      "name": "field_name",
      "reason": "Must not be blank"
    }
  ]
}
```

### 3.4 Pagination & Sorting Standard
- Every list endpoint must accept `page` (0-indexed, default 0), `size` (default 20, maximum 100), and `sort` (e.g., `sort=expiryDate,asc`).
- List responses must return a paginated envelope:
```json
{
  "content": [ ... ],
  "page": 0,
  "size": 20,
  "totalElements": 142,
  "totalPages": 8,
  "last": false
}
```

### 3.5 Idempotency Key Requirement
- Critical mutation endpoints (`POST /api/v1/inventory/inbound`, `POST /api/v1/stock-transfers`, `POST /api/v1/shipments/{id}/receive`, `POST /api/v1/inventory/adjust`) must support an optional `X-Idempotency-Key` HTTP header.
- If a repeated key is received within a 24-hour window, the backend must return the cached original response without re-executing ledger mutations.

---

## 4. Database & Persistence Rules

### 4.1 Flyway Migration Rules
1. **Immutable Migrations:** Never modify an already-executed Flyway migration file. Always create a new sequential version file (e.g., `V4__add_cold_chain_to_batches.sql`).
2. **Deterministic SQL:** Use explicit PostgreSQL syntax. Specify `NOT NULL`, `DEFAULT`, `CHECK`, and `REFERENCES` constraints on every table.
3. **Primary Keys:** Use `UUID` (generated via `gen_random_uuid()`) for all transactional entities to prevent enumeration attacks and support offline generation.

### 4.2 Indexing Rules
- Every Foreign Key column must have an explicit database index.
- Composite indexes must be added for high-cardinality multi-column query filters (e.g., `(warehouse_id, batch_id)`, `(expiry_date, status)`).

### 4.3 Immutable Double-Entry Ledger Invariant
- The tables `inventory_journal_entries`, `inventory_ledger_lines`, and `audit_logs` are strictly **APPEND-ONLY**.
- Application code, repositories, and database triggers must never issue `UPDATE` or `DELETE` statements against these tables.

---

## 5. Inventory Safety & Integrity Rules

```text
       ┌────────────────────────────────────────────────────────┐
       │                INVENTORY SAFETY INVARIANTS             │
       ├────────────────────────────────────────────────────────┤
       │ 1. Available Stock + Reserved Stock + Quarantined =    │
       │    Total Physical Stock (Always)                       │
       │ 2. Available Stock >= 0 (No negative inventory)        │
       │ 3. Sum(Debits) == Sum(Credits) on every Journal Entry  │
       │ 4. Batch Expiry Date > Current Date for active dispatch│
       │ 5. Stock Transfers require FEFO allocation by default  │
       │ 6. Optimistic Lock (@Version) mandatory on Balances   │
       └────────────────────────────────────────────────────────┘
```

1. **Negative Inventory Prohibition:**
   - Under no circumstances may `available_quantity` or `physical_quantity` drop below 0.
   - Enforce via database constraint: `CHECK (available_quantity >= 0)` and JPA pre-checks.
2. **Double-Entry Journal & 3-Bucket Tracking Invariant:**
   - Every inventory movement must be executed through `DoubleEntryLedgerService` creating an `inventory_journal_entries` record with balanced `inventory_ledger_lines`.
   - The ledger line must record explicit deltas and resulting balances across all 3 buckets (`available`, `reserved`, `quarantined`).
   - The service must assert $\sum \text{Debit Quantities} = \sum \text{Credit Quantities}$ prior to committing the database transaction.
3. **Optimistic Locking:**
   - `InventoryBalance` entity must include `@Version Long version`. Any concurrent modification conflict must throw `OptimisticLockingFailureException` and trigger an HTTP 409 response.
4. **FEFO Allocation & Mandatory Override Auditing:**
   - When picking batches for transfer orders, batches with the earliest valid expiry date must be reserved first (`ORDER BY b.expiry_date ASC, b.id ASC FOR UPDATE`).
   - Batches with status `EXPIRED` or `QUARANTINED` must be filtered out of available stock calculations.
   - **No Silent FEFO Bypass:** Manual overrides require `override_reason`, `overridden_by`, `overridden_at` and a synchronous critical audit record committed within the same database transaction.
5. **Configurable Receiving Shelf-Life:**
   - Inbound receiving must validate expiry date $\ge \text{Current Date} + \text{medicine.min\_receiving\_shelf\_life\_days}$ (default 90 days).
6. **Duplicate Receiving Protection:**
   - A shipment or transfer cannot be marked `RECEIVED` or `COMPLETED` more than once. The receiving transaction must check current status equals `IN_TRANSIT` or `OUT_FOR_DELIVERY` inside an atomic lock.

---

## 6. Security & Secret Handling Rules

1. **Zero Hardcoded Secrets:**
   - Passwords, JWT secrets, database connection strings, and third-party API keys must never appear in Java source files, unit tests, or frontend code.
   - Secrets must be injected via environment variables (e.g., `${MEDTRACK_JWT_SECRET}`, `${MEDTRACK_DB_PASSWORD}`).
2. **Single-Use Refresh Token Rotation (RTR) & Reuse Invalidation:**
   - Refresh tokens must be single-use and assigned a `family_id`.
   - On refresh, the presented token is invalidated and replaced with a new pair.
   - If a revoked token is presented, the backend must instantly revoke all tokens in that `family_id` (token compromise defense).
3. **Server-Side Authorization Enforcement:**
   - Client-side route guards are for UX only.
   - Every protected backend endpoint must have an explicit Spring Security annotation:
     ```java
     @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('INVENTORY_WRITE')")
     ```
4. **Password Security:**
   - Store passwords using **BCrypt** with minimum strength 12 or **Argon2id**. Plaintext passwords must never be stored, logged, or serialized.
5. **Sanitized Exception Handling:**
   - Production error responses must never expose SQL queries, JPA stack traces, or internal server paths.

---

## 7. Logging & Synchronous Audit Rules

### 7.1 Synchronous Transactional Audit Commit
- Critical audit events (inventory ledger movements, FEFO overrides, stock adjustments, role changes) must be committed **synchronously within the exact same database transaction boundary** as the business entity update.
- Secondary notifications (email dispatch, push alerts, Prometheus metrics) must be published asynchronously post-commit via `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`.

### 7.2 What Must Be Logged
- **Authentication Events:** Login success, login failure (with IP and username), token rotation, logout.
- **Inventory State Mutations:** Inbound receipt, FEFO allocation, manual overrides, pick confirmation, dispatch, delivery receipt, manual write-offs.
- **Exceptions:** Logged at `ERROR` level with full stack trace and correlation `traceId`.
- **External API Calls:** Logged at `INFO` (or `DEBUG`) with endpoint URI, response status code, and latency in milliseconds.

### 7.2 What Must NEVER Be Logged
- User passwords or raw credential strings.
- JWT bearer tokens or refresh token hashes.
- PII (phone numbers, full personal residential addresses) unless masked.
- Raw HTTP Authorization headers.

### 7.3 Log Format Standard
All application logs must be structured JSON containing:
```json
{
  "timestamp": "2026-08-26T21:12:00.123Z",
  "level": "INFO",
  "traceId": "c4b2a8d1-5e3f-4a92-b01f-9a1122334455",
  "userId": "018e42b2-8c11-7001-9bc2-3c8172901a1a",
  "logger": "com.medtrack.inventory.service.InboundService",
  "message": "Inbound consignment received successfully. Batch BAT-2026-08A created with 500 units.",
  "context": {
    "warehouseId": "018e42b2-8c11-7001-9bc2-3c8172901b2b",
    "batchNumber": "BAT-2026-08A",
    "quantity": 500
  }
}
```

---

## 8. Dependency Management Rules

### 8.1 Approved Tech Stack (MEDTRACK 2026 TECHNOLOGY BASELINE)
- **Backend:**
  - Java 25 LTS
  - Spring Boot 4.1.1 (`spring-boot-starter-web`, `spring-boot-starter-security`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`)
  - Spring Security 7.x (Stateless JWT, RBAC, Single-Use RTR)
  - Hibernate ORM 7.4.x & Spring Data JPA
  - Maven 3.9.16
  - Flyway Core (Database migrations)
  - PostgreSQL JDBC Driver (connecting to PostgreSQL 18.6)
  - MapStruct (for zero-reflection compile-time DTO mapping)
  - JJWT (for standard JWT handling)
  - Google ZXing (for barcode & QR generation)
  - Springdoc OpenAPI (for Swagger UI / OpenAPI 3.0 specs)
- **Database:**
  - PostgreSQL 18.6
  - UUIDv7 where appropriate
  - JSONB (for audit log diffs and unstructured event payloads)
  - ACID transactions
  - Optimistic locking (`@Version`)
  - Pessimistic locking for FEFO batch reservation
  - Immutable ledger constraints (append-only journal entries)
- **Frontend:**
  - React 19.2.x & TypeScript 5.9.x
  - Vite 8.1.x
  - Node.js 24.20 LTS
  - Tailwind CSS 4.x
  - TanStack Query (React Query)
  - React Router
  - Zustand (for lightweight global client state)
  - Lucide React (for lightweight, consistent iconography)
  - React Hook Form + Zod (for type-safe schema validation)
  - Leaflet & React-Leaflet (for map rendering)
  - html5-qrcode (for camera barcode scanning)
  - Axios (with standard interceptors)
- **Testing:**
  - JUnit 5 & Mockito (unit tests)
  - Testcontainers (PostgreSQL 18.6 integration tests)
  - Vitest & React Testing Library (frontend unit/component tests)
  - Playwright (end-to-end browser workflows)
- **Infrastructure:**
  - Docker & Docker Compose v2
  - PostgreSQL 18.6 container
  - Nginx (reverse proxy and static asset delivery)

### 8.2 Prohibited / Blacklisted Libraries
- **No Heavy UI Component Libraries:** Do not install Material-UI (MUI), Ant Design, or Bootstrap. Use Tailwind CSS 4.x with headless primitive components adhering to `design.md`.
- **No Unjustified Messaging Brokers in MVP:** Do not introduce Kafka, RabbitMQ, or ActiveMQ for the MVP. Use Spring internal application events and PostgreSQL transaction boundaries.
- **No Unmanaged Connection Pools:** Do not use raw JDBC connections; always use Spring Boot's default HikariCP.

### 8.3 Frontend Layered API & State Architecture
- **Strict Data Access Flow:**
  $$\text{React Component} \longrightarrow \text{Custom Feature Hook (\texttt{use...})} \longrightarrow \text{Service API Client} \longrightarrow \text{Spring Boot API}$$
- React components must **never invoke Axios directly**.
- All server queries and mutations must use TanStack Query hooks (`useQuery`, `useMutation`) encapsulated inside domain feature hooks.

---

## 9. Testing Standards & Coverage Thresholds

1. **Unit Testing:**
   - All domain services (FEFO allocation engine, Expiry calculator, Inventory ledger arithmetic) must have **$\ge 90\%$ branch test coverage** using JUnit 5 and Mockito.
2. **Integration Testing:**
   - Database repositories and transactional services must be tested against real PostgreSQL 18.6 instances using **Testcontainers**.
   - H2 in-memory database is prohibited for integration testing to prevent dialect and constraint discrepancy bugs.
3. **Controller Slice Testing:**
   - Use `@WebMvcTest` to verify HTTP status codes, security filters, and validation triggers on every endpoint.
4. **Mandatory Concurrency Tests:**
   - Concurrency tests must verify that simultaneous stock reservation requests on the same batch do not result in negative inventory or double-allocation.
5. **Frontend Testing:**
   - UI components and state stores tested using Vitest and React Testing Library; end-to-end critical paths verified with Playwright.
