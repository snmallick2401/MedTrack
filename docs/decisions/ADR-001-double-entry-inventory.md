# ADR-001: True Double-Entry Inventory Accounting with 3-Bucket Delta Tracking

**Status:** Accepted  
**Date:** 2026-08-26  
**Deciders:** MedTrack Architecture Team  

---

## Context
Traditional pharmaceutical inventory systems often rely on single mutable columns (e.g. `UPDATE stock SET quantity = quantity - X`) or simple single-entry movement logs. In complex supply chains with multi-depot distribution and transit hops, these approaches create major problems:
1. **Goods in Transit Blind Spot:** Once dispatched from a central warehouse, goods vanish from active inventory balance sheets before being received at branch clinics.
2. **Bucket Transition Ambiguity:** Stock movements transition between `available`, `reserved`, and `quarantined` states. Without explicit before/delta/after logging per bucket, forensic auditing of concurrent allocations is difficult.
3. **Audit Failure:** Direct mutation of stock records leaves no immutable mathematical proof that physical stock was neither fabricated nor lost without a trace.

---

## Decision
MedTrack adopts an authentic **Double-Entry Inventory Asset Bookkeeping System** coupled with a high-performance materialized snapshot table:

1. **Double-Entry Journal & Balanced Ledger Lines:**
   - Every inventory movement creates an `inventory_journal_entries` record with two or more `inventory_ledger_lines`.
   - The system asserts $\sum \text{Debits} = \sum \text{Credits}$ before committing.
   - Account types include `WAREHOUSE_ACTIVE`, `IN_TRANSIT`, `QUARANTINE_HOLD`, `SUPPLIER_OFFSET`, `DISPENSE_EXPENSE`, and `WRITE_OFF_LOSS`.
2. **Explicit 3-Bucket State Snapshots:**
   - Every ledger line explicitly records `available_before/delta/after`, `reserved_before/delta/after`, and `quarantined_before/delta/after`.
3. **High-Performance Snapshot Table (`inventory_balances`):**
   - Stores current physical totals per batch/warehouse with `@Version` optimistic locking to avoid scanning millions of historical ledger lines during real-time queries.
4. **Synchronous Transactional Audit:**
   - Balance update, ledger lines, and critical audit log entries are committed within the exact same database transaction boundary.

---

## Consequences
- **Positive:**
  - Complete mathematical balance proof across all inventory accounts.
  - Zero lost stock during multi-day carrier transit (goods remain in the `IN_TRANSIT` account).
  - Unambiguous forensic trail for every allocation and manual override.
  - Zero negative inventory states guaranteed by database check constraints.
- **Negative / Tradeoffs:**
  - Additional insert overhead (1 journal header + 2 ledger lines per movement), which is negligible given PostgreSQL's write throughput and modern hardware capabilities.
