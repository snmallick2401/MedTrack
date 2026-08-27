import { FormEvent, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowDown,
  ArrowUp,
  ArrowUpDown,
  ChevronLeft,
  ChevronRight,
  Eye,
  Pill,
  Plus,
  Save,
  Search,
  X
} from "lucide-react";
import { masterDataApi } from "../../services/masterDataApi";
import { ErrorBanner, Empty, Loading } from "../../components/feedback/States";
import { StatusBadge } from "../../components/ui/StatusBadge";
import { errorMessage } from "../../utils/errors";
import type { Medicine, Warehouse } from "../../types/api";

function PageTitle({ title, subtitle, action }: { title: string; subtitle?: string; action?: React.ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-center justify-between gap-3 border-b border-border pb-4">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-ink">{title}</h1>
        <p className="mt-1 text-xs text-muted">
          {subtitle ?? "Authoritative master data catalog applied across all enterprise warehouses and depots."}
        </p>
      </div>
      {action}
    </div>
  );
}

function Button({ children, ...props }: React.ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button
      {...props}
      className={`inline-flex min-h-10 items-center justify-center gap-2 rounded-lg bg-ink px-4 text-sm font-semibold text-canvas shadow-xs transition hover:opacity-90 active:scale-[0.98] disabled:bg-surface-strong disabled:text-muted disabled:border disabled:border-border disabled:opacity-85 disabled:cursor-not-allowed ${
        props.className ?? ""
      }`}
    >
      {children}
    </button>
  );
}

function Field({ label, children, required }: { label: string; children: React.ReactNode; required?: boolean }) {
  return (
    <label className="grid gap-1.5 text-xs font-semibold text-ink">
      <span>
        {label} {required && <span className="text-danger">*</span>}
      </span>
      {children}
    </label>
  );
}

function Panel({ children, className = "" }: { children: React.ReactNode; className?: string }) {
  return <div className={`rounded-xl border border-border bg-canvas p-5 shadow-xs ${className}`}>{children}</div>;
}

function FormError({ error }: { error: unknown }) {
  return error ? <ErrorBanner message={errorMessage(error)} /> : null;
}

function formatCategoryTitle(categoryCode?: string): string {
  if (!categoryCode) return "Standard";
  return categoryCode
    .toLowerCase()
    .split("_")
    .map(word => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ");
}

export function MedicinesPage() {
  const [search, setSearch] = useState("");
  const [show, setShow] = useState(false);
  const [statusFilter, setStatusFilter] = useState<"ALL" | "ACTIVE" | "DISCONTINUED">("ALL");
  const [sortField, setSortField] = useState<"sku" | "genericName" | "categoryCode" | "status">("sku");
  const [sortAsc, setSortAsc] = useState(true);
  const [selectedMedicine, setSelectedMedicine] = useState<Medicine | null>(null);

  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);

  const q = useQuery({
    queryKey: ["medicines", search],
    queryFn: () => masterDataApi.medicines(0, search)
  });

  const allMedicines = q.data?.content ?? [];

  // Filter medicines by search & status
  const filteredMedicines = useMemo(() => {
    return allMedicines.filter(m => {
      if (statusFilter !== "ALL" && m.status !== statusFilter) return false;
      if (!search.trim()) return true;
      const term = search.toLowerCase();
      return (
        m.sku.toLowerCase().includes(term) ||
        m.genericName.toLowerCase().includes(term) ||
        (m.brandName && m.brandName.toLowerCase().includes(term)) ||
        m.categoryCode.toLowerCase().includes(term)
      );
    });
  }, [allMedicines, search, statusFilter]);

  // Sort filtered medicines
  const sortedMedicines = useMemo(() => {
    return [...filteredMedicines].sort((a, b) => {
      const valA = (a[sortField] ?? "").toString().toLowerCase();
      const valB = (b[sortField] ?? "").toString().toLowerCase();
      return sortAsc ? valA.localeCompare(valB) : valB.localeCompare(valA);
    });
  }, [filteredMedicines, sortField, sortAsc]);

  // Client-side pagination over query results
  const totalItems = sortedMedicines.length;
  const totalPages = Math.max(1, Math.ceil(totalItems / pageSize));
  const currentPage = Math.min(page, totalPages - 1);
  const paginatedMedicines = useMemo(() => {
    const start = Math.max(0, currentPage) * pageSize;
    return sortedMedicines.slice(start, start + pageSize);
  }, [sortedMedicines, currentPage, pageSize]);

  const handleSort = (field: "sku" | "genericName" | "categoryCode" | "status") => {
    if (sortField === field) {
      setSortAsc(prev => !prev);
    } else {
      setSortField(field);
      setSortAsc(true);
    }
  };

  const pageStart = totalItems === 0 ? 0 : Math.max(0, currentPage) * pageSize + 1;
  const pageEnd = Math.min((Math.max(0, currentPage) + 1) * pageSize, totalItems);

  return (
    <section className="space-y-6">
      <PageTitle
        title="Medicines formulation catalog"
        subtitle="Global master data repository defining pharmaceutical formulations, dosage strengths, and minimum threshold constraints."
        action={
          <Button onClick={() => setShow(v => !v)}>
            <Plus size={16} />
            {show ? "Close registration form" : "New medicine"}
          </Button>
        }
      />

      {show && (
        <MedicineForm
          onComplete={() => {
            setShow(false);
            q.refetch();
          }}
        />
      )}

      {/* Control Bar: Search + Status Filter Tabs */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-1 flex-wrap items-center gap-3">
          <div className="relative flex flex-1 min-w-[280px] max-w-md items-center">
            <Search size={16} className="pointer-events-none absolute left-3.5 text-muted z-10" />
            <input
              id="medicine-search"
              value={search}
              onChange={e => {
                setSearch(e.target.value);
                setPage(0);
              }}
              className="input search-input h-10 text-xs w-full"
              placeholder="Search by SKU, generic name, brand…"
            />
            {search && (
              <button
                onClick={() => setSearch("")}
                className="absolute right-3 text-muted hover:text-ink"
                aria-label="Clear search"
              >
                <X size={14} />
              </button>
            )}
          </div>

          {/* Unified Segmented Control */}
          <div className="flex h-10 items-center rounded-lg border border-border bg-surface-soft p-1 text-xs shrink-0">
            {(["ALL", "ACTIVE", "DISCONTINUED"] as const).map(st => {
              const isActive = statusFilter === st;
              return (
                <button
                  key={st}
                  onClick={() => {
                    setStatusFilter(st);
                    setPage(0);
                  }}
                  className={`h-full px-3.5 rounded-md text-xs font-semibold transition flex items-center justify-center ${
                    isActive
                      ? "bg-canvas text-ink shadow-xs border border-border/80"
                      : "text-muted hover:text-ink hover:bg-canvas/50"
                  }`}
                >
                  {st === "ALL" ? "All statuses" : st.charAt(0) + st.slice(1).toLowerCase()}
                </button>
              );
            })}
          </div>
        </div>

        {/* Count Indicator Pill */}
        <div className="flex h-10 items-center rounded-lg border border-border bg-surface-soft px-3.5 text-xs text-muted shrink-0">
          Showing&nbsp;<strong className="text-ink">{pageStart}–{pageEnd}</strong>&nbsp;of&nbsp;<strong className="text-ink">{totalItems}</strong>&nbsp;medicines
        </div>
      </div>

      {q.isLoading ? (
        <Loading />
      ) : q.isError ? (
        <ErrorBanner message={errorMessage(q.error)} />
      ) : !paginatedMedicines.length ? (
        <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-border bg-canvas p-12 text-center">
          <Pill size={36} className="text-muted/40" />
          <h2 className="mt-3 text-base font-semibold text-ink">No medicines found</h2>
          <p className="mt-1 text-xs text-muted max-w-sm">
            {search || statusFilter !== "ALL"
              ? "No formulation records match the active search query or status filter."
              : "No medicine formulations registered yet. Click 'New medicine' to create one."}
          </p>
          {(search || statusFilter !== "ALL") && (
            <button
              onClick={() => {
                setSearch("");
                setStatusFilter("ALL");
              }}
              className="mt-4 rounded-lg border border-border bg-surface-soft px-3 py-1.5 text-xs font-semibold text-ink hover:bg-canvas"
            >
              Reset search filters
            </button>
          )}
        </div>
      ) : (
        <Panel className="overflow-hidden p-0">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="border-b border-border bg-surface-soft/60 text-[11px] font-semibold uppercase tracking-wider text-muted">
                <tr>
                  <th
                    className="cursor-pointer px-4 py-3 hover:text-ink w-[160px]"
                    onClick={() => handleSort("sku")}
                  >
                    <div className="flex items-center gap-1.5">
                      <span>SKU</span>
                      {sortField === "sku" ? (
                        sortAsc ? <ArrowUp size={13} /> : <ArrowDown size={13} />
                      ) : (
                        <ArrowUpDown size={13} className="text-muted/40" />
                      )}
                    </div>
                  </th>
                  <th
                    className="cursor-pointer px-4 py-3 hover:text-ink min-w-[220px]"
                    onClick={() => handleSort("genericName")}
                  >
                    <div className="flex items-center gap-1.5">
                      <span>Medicine & Brand</span>
                      {sortField === "genericName" ? (
                        sortAsc ? <ArrowUp size={13} /> : <ArrowDown size={13} />
                      ) : (
                        <ArrowUpDown size={13} className="text-muted/40" />
                      )}
                    </div>
                  </th>
                  <th className="px-4 py-3 w-[150px]">Form & Strength</th>
                  <th
                    className="cursor-pointer px-4 py-3 hover:text-ink w-[130px]"
                    onClick={() => handleSort("categoryCode")}
                  >
                    <div className="flex items-center gap-1.5">
                      <span>Category</span>
                      {sortField === "categoryCode" ? (
                        sortAsc ? <ArrowUp size={13} /> : <ArrowDown size={13} />
                      ) : (
                        <ArrowUpDown size={13} className="text-muted/40" />
                      )}
                    </div>
                  </th>
                  <th className="px-4 py-3 w-[150px]">Thresholds</th>
                  <th
                    className="cursor-pointer px-4 py-3 hover:text-ink w-[110px]"
                    onClick={() => handleSort("status")}
                  >
                    <div className="flex items-center gap-1.5">
                      <span>Status</span>
                      {sortField === "status" ? (
                        sortAsc ? <ArrowUp size={13} /> : <ArrowDown size={13} />
                      ) : (
                        <ArrowUpDown size={13} className="text-muted/40" />
                      )}
                    </div>
                  </th>
                  <th className="px-4 py-3 text-right w-[90px]">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {paginatedMedicines.map(m => (
                  <tr key={m.id} className="transition hover:bg-surface-soft/40">
                    <td className="px-4 py-3 font-mono font-semibold text-ink">{m.sku}</td>
                    <td className="px-4 py-3">
                      <strong className="text-sm font-semibold text-ink">{m.genericName}</strong>
                      <span className="mt-0.5 block text-[11px] text-muted">
                        {m.brandName ? `Brand: ${m.brandName}` : "Standard generic formulation"}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <span className="inline-flex items-center rounded-md border border-border bg-surface-soft px-2 py-0.5 font-mono text-[11px] font-medium text-ink">
                        {m.dosageForm} · {m.strength}
                      </span>
                    </td>
                    <td className="px-4 py-3 font-medium text-body">
                      {formatCategoryTitle(m.categoryCode)}
                    </td>
                    <td className="px-4 py-3 font-mono text-[11px] text-muted">
                      <div>Min stock: <strong className="text-ink">{m.minStockThreshold} {m.unitOfMeasure}</strong></div>
                      <div>Min shelf life: <strong className="text-ink">{m.minReceivingShelfLifeDays}d</strong></div>
                    </td>
                    <td className="px-4 py-3">
                      <StatusBadge status={m.status} />
                    </td>
                    <td className="px-4 py-3 text-right">
                      <button
                        onClick={() => setSelectedMedicine(m)}
                        className="inline-flex items-center gap-1 rounded-md border border-border bg-canvas px-2.5 py-1 text-xs font-semibold text-ink shadow-xs hover:bg-surface-soft"
                        title="View complete medicine specifications"
                        aria-label={`View details for ${m.genericName}`}
                      >
                        <Eye size={13} />
                        <span>View</span>
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Pagination Controls */}
          <div className="flex flex-wrap items-center justify-between gap-3 border-t border-border bg-surface-soft/40 px-4 py-3 text-xs">
            <div className="flex items-center gap-2 text-muted">
              <span>Items per page:</span>
              <select
                value={pageSize}
                onChange={e => {
                  setPageSize(Number(e.target.value));
                  setPage(0);
                }}
                className="rounded border border-border bg-canvas px-2 py-1 text-xs text-ink"
              >
                <option value="10">10</option>
                <option value="25">25</option>
                <option value="50">50</option>
              </select>
            </div>

            <div className="flex items-center gap-2">
              <button
                disabled={currentPage === 0}
                onClick={() => setPage(p => Math.max(0, p - 1))}
                className="inline-flex items-center gap-1 rounded border border-border bg-canvas px-2.5 py-1 text-xs font-medium text-ink shadow-xs hover:bg-surface-soft disabled:opacity-40 disabled:cursor-not-allowed"
                aria-label="Previous page"
              >
                <ChevronLeft size={14} />
                <span>Prev</span>
              </button>
              <span className="font-mono text-muted">
                Page <strong className="text-ink">{currentPage + 1}</strong> of{" "}
                <strong className="text-ink">{totalPages}</strong>
              </span>
              <button
                disabled={currentPage >= totalPages - 1}
                onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
                className="inline-flex items-center gap-1 rounded border border-border bg-canvas px-2.5 py-1 text-xs font-medium text-ink shadow-xs hover:bg-surface-soft disabled:opacity-40 disabled:cursor-not-allowed"
                aria-label="Next page"
              >
                <span>Next</span>
                <ChevronRight size={14} />
              </button>
            </div>
          </div>
        </Panel>
      )}

      {/* Details Modal */}
      {selectedMedicine && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="medicine-details-title"
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm"
          onMouseDown={() => setSelectedMedicine(null)}
        >
          <div
            className="w-full max-w-lg rounded-xl border border-border bg-canvas p-6 shadow-2xl"
            onMouseDown={e => e.stopPropagation()}
          >
            <div className="flex items-start justify-between border-b border-border pb-3">
              <div>
                <h2 id="medicine-details-title" className="text-lg font-bold text-ink">
                  {selectedMedicine.genericName}
                </h2>
                <p className="font-mono text-xs text-muted">{selectedMedicine.sku}</p>
              </div>
              <button
                onClick={() => setSelectedMedicine(null)}
                className="rounded p-1 text-muted hover:text-ink"
                aria-label="Close medicine details"
              >
                <X size={18} />
              </button>
            </div>

            <div className="mt-4 grid grid-cols-2 gap-3 text-xs">
              <div className="rounded-lg border border-border bg-surface-soft p-3">
                <span className="text-muted">Brand Name</span>
                <p className="mt-1 font-semibold text-ink">{selectedMedicine.brandName ?? "Standard Generic"}</p>
              </div>
              <div className="rounded-lg border border-border bg-surface-soft p-3">
                <span className="text-muted">Dosage Form</span>
                <p className="mt-1 font-semibold text-ink">{selectedMedicine.dosageForm}</p>
              </div>
              <div className="rounded-lg border border-border bg-surface-soft p-3">
                <span className="text-muted">Strength Specification</span>
                <p className="mt-1 font-mono font-semibold text-ink">{selectedMedicine.strength}</p>
              </div>
              <div className="rounded-lg border border-border bg-surface-soft p-3">
                <span className="text-muted">Unit of Measure</span>
                <p className="mt-1 font-mono font-semibold text-ink">{selectedMedicine.unitOfMeasure}</p>
              </div>
              <div className="rounded-lg border border-border bg-surface-soft p-3">
                <span className="text-muted">Category</span>
                <p className="mt-1 font-semibold text-ink">{formatCategoryTitle(selectedMedicine.categoryCode)}</p>
              </div>
              <div className="rounded-lg border border-border bg-surface-soft p-3">
                <span className="text-muted">Storage Temperature</span>
                <p className="mt-1 font-mono font-semibold text-ink">{selectedMedicine.storageTemp ?? "ROOM_TEMP"}</p>
              </div>
              <div className="rounded-lg border border-border bg-surface-soft p-3">
                <span className="text-muted">Min Stock Threshold</span>
                <p className="mt-1 font-mono font-semibold text-ink">
                  {selectedMedicine.minStockThreshold} {selectedMedicine.unitOfMeasure}
                </p>
              </div>
              <div className="rounded-lg border border-border bg-surface-soft p-3">
                <span className="text-muted">Min Receiving Shelf Life</span>
                <p className="mt-1 font-mono font-semibold text-ink">
                  {selectedMedicine.minReceivingShelfLifeDays} Days
                </p>
              </div>
            </div>

            {selectedMedicine.description && (
              <div className="mt-3 rounded-lg border border-border bg-surface-soft p-3 text-xs">
                <span className="text-muted">Formulation Notes</span>
                <p className="mt-1 text-body">{selectedMedicine.description}</p>
              </div>
            )}

            <div className="mt-5 flex justify-end">
              <button
                onClick={() => setSelectedMedicine(null)}
                className="rounded-lg border border-border bg-canvas px-4 py-2 text-xs font-semibold text-ink hover:bg-surface-soft"
              >
                Close specifications
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}

function MedicineForm({ onComplete }: { onComplete: () => void }) {
  const [error, setError] = useState<unknown>();
  const categoriesQuery = useQuery({
    queryKey: ["categories"],
    queryFn: masterDataApi.categories
  });

  const mutation = useMutation({
    mutationFn: masterDataApi.createMedicine,
    onSuccess: onComplete,
    onError: setError
  });

  function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const d = new FormData(e.currentTarget);
    mutation.mutate({
      sku: String(d.get("sku")),
      genericName: String(d.get("genericName")),
      brandName: String(d.get("brandName")) || undefined,
      categoryId: String(d.get("categoryId")),
      dosageForm: String(d.get("dosageForm")),
      strength: String(d.get("strength")),
      unitOfMeasure: String(d.get("unitOfMeasure")),
      storageTemp: String(d.get("storageTemp")) || undefined,
      minStockThreshold: Number(d.get("minStockThreshold") || 0),
      minReceivingShelfLifeDays: Number(d.get("minReceivingShelfLifeDays") || 90),
      status: String(d.get("status")),
      description: String(d.get("description")) || undefined
    });
  }

  return (
    <div className="mb-6">
      <Panel>
        <div className="mb-4 border-b border-border pb-3">
          <h2 className="text-base font-semibold text-ink">Register new medicine SKU</h2>
          <p className="text-xs text-muted">
            Creates an enterprise-wide pharmaceutical master formulation available across all inventory nodes.
          </p>
        </div>
        <form onSubmit={submit} className="grid gap-3 md:grid-cols-3">
          <Field label="SKU Code" required>
            <input required pattern="MED-[A-Z]{2,12}-[0-9]{4,6}" name="sku" placeholder="e.g. MED-AMOX-00100" className="input font-mono text-xs" />
          </Field>
          <Field label="Generic Name" required>
            <input required name="genericName" className="input text-xs" placeholder="e.g. Amoxicillin Trihydrate" />
          </Field>
          <Field label="Brand / Trade Name">
            <input name="brandName" className="input text-xs" placeholder="e.g. Amoxil Standard" />
          </Field>
          <Field label="Therapeutic Category" required>
            <select required name="categoryId" className="input text-xs">
              <option value="">Select therapeutic category…</option>
              {categoriesQuery.data?.map(c => (
                <option key={c.id} value={c.id}>{c.code} — {c.name}</option>
              ))}
            </select>
          </Field>
          <Field label="Dosage Form" required>
            <select required name="dosageForm" defaultValue="TABLET" className="input text-xs">
              <option value="TABLET">TABLET</option>
              <option value="CAPSULE">CAPSULE</option>
              <option value="INJECTION">INJECTION</option>
              <option value="SYRUP">SYRUP</option>
              <option value="OINTMENT">OINTMENT</option>
              <option value="IV_FLUID">IV_FLUID</option>
            </select>
          </Field>
          <Field label="Strength Formulation" required>
            <input required name="strength" className="input text-xs font-mono" placeholder="e.g. 500mg" />
          </Field>
          <Field label="Unit of Measure (UoM)" required>
            <input required name="unitOfMeasure" defaultValue="BOX" className="input text-xs font-mono" placeholder="e.g. BOX, VIAL, PACK" />
          </Field>
          <Field label="Storage Temperature">
            <input name="storageTemp" className="input text-xs font-mono" placeholder="ROOM_TEMP" defaultValue="ROOM_TEMP" />
          </Field>
          <Field label="Minimum Stock Threshold">
            <input name="minStockThreshold" type="number" min="0" defaultValue="10" className="input text-xs" />
          </Field>
          <Field label="Min Receiving Shelf Life (Days)">
            <input name="minReceivingShelfLifeDays" type="number" min="0" defaultValue="90" className="input text-xs" />
          </Field>
          <Field label="Status">
            <select name="status" defaultValue="ACTIVE" className="input text-xs">
              <option value="ACTIVE">ACTIVE</option>
              <option value="DISCONTINUED">DISCONTINUED</option>
            </select>
          </Field>
          <Field label="Formulation Notes">
            <input name="description" className="input text-xs" placeholder="Optional clinical notes" />
          </Field>
          <div className="md:col-span-3 pt-2">
            <FormError error={error} />
            <Button type="submit" disabled={mutation.isPending}>
              <Save size={16} />
              {mutation.isPending ? "Registering formulation…" : "Create medicine"}
            </Button>
          </div>
        </form>
      </Panel>
    </div>
  );
}

export function BatchesPage() {
  const [show, setShow] = useState(false);
  const q = useQuery({ queryKey: ["batches"], queryFn: () => masterDataApi.batches() });

  return (
    <section>
      <PageTitle
        title="Batches"
        action={
          <Button onClick={() => setShow(v => !v)}>
            <Plus size={16} />
            {show ? "Close form" : "New batch"}
          </Button>
        }
      />
      {show && <BatchForm onComplete={() => { setShow(false); q.refetch(); }} />}
      {q.isLoading ? (
        <Loading />
      ) : q.isError ? (
        <ErrorBanner message={errorMessage(q.error)} />
      ) : !q.data?.content.length ? (
        <Empty title="No batches found" />
      ) : (
        <Panel>
          <div className="overflow-x-auto">
            <table className="w-full text-left">
              <thead className="text-xs uppercase text-muted">
                <tr>
                  <th className="py-2">Batch</th>
                  <th>Medicine</th>
                  <th>Expiry</th>
                  <th>Initial qty.</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {q.data.content.map(b => (
                  <tr key={b.id} className="hover:bg-surface-soft/40">
                    <td className="py-3 font-mono font-medium text-ink">{b.batchNumber}</td>
                    <td className="text-sm">{b.medicineId}</td>
                    <td className="text-sm text-muted">{b.expiryDate}</td>
                    <td className="font-mono text-sm">{b.initialQuantity}</td>
                    <td><StatusBadge status={b.status} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Panel>
      )}
    </section>
  );
}

function BatchForm({ onComplete }: { onComplete: () => void }) {
  const [error, setError] = useState<unknown>();
  const medicines = useQuery({ queryKey: ["medicinesList"], queryFn: () => masterDataApi.medicines(0) });
  const suppliers = useQuery({ queryKey: ["suppliersList"], queryFn: () => masterDataApi.suppliers(0) });

  const m = useMutation({
    mutationFn: masterDataApi.createBatch,
    onSuccess: onComplete,
    onError: setError
  });

  function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const d = new FormData(e.currentTarget);
    m.mutate({
      batchNumber: String(d.get("batchNumber")),
      medicineId: String(d.get("medicineId")),
      supplierId: String(d.get("supplierId")),
      manufacturingDate: String(d.get("manufacturingDate")),
      expiryDate: String(d.get("expiryDate")),
      initialQuantity: Number(d.get("initialQuantity")),
      status: String(d.get("status"))
    });
  }

  return (
    <div className="mb-6">
      <Panel>
        <h2 className="mb-3 font-semibold text-ink">Register new batch</h2>
        <form onSubmit={submit} className="grid gap-3 md:grid-cols-3">
          <Field label="Batch number">
            <input required name="batchNumber" className="input" placeholder="e.g. BAT-2026-0001" />
          </Field>
          <Field label="Medicine">
            <select required name="medicineId" className="input">
              <option value="">Select medicine</option>
              {medicines.data?.content.map(med => (
                <option key={med.id} value={med.id}>{med.sku} — {med.genericName}</option>
              ))}
            </select>
          </Field>
          <Field label="Supplier">
            <select required name="supplierId" className="input">
              <option value="">Select supplier</option>
              {suppliers.data?.content.map(sup => (
                <option key={sup.id} value={sup.id}>{sup.code} — {sup.name}</option>
              ))}
            </select>
          </Field>
          <Field label="Manufactured date">
            <input required type="date" name="manufacturingDate" className="input" />
          </Field>
          <Field label="Expiry date">
            <input required type="date" name="expiryDate" className="input" />
          </Field>
          <Field label="Initial quantity">
            <input required min="1" type="number" name="initialQuantity" className="input" defaultValue="100" />
          </Field>
          <Field label="Status">
            <select name="status" defaultValue="ACTIVE" className="input">
              <option value="ACTIVE">ACTIVE</option>
              <option value="QUARANTINED">QUARANTINED</option>
            </select>
          </Field>
          <div className="md:col-span-3">
            <FormError error={error} />
            <Button disabled={m.isPending}>
              <Save size={16} />
              {m.isPending ? "Creating…" : "Create batch"}
            </Button>
          </div>
        </form>
      </Panel>
    </div>
  );
}

export function WarehousesPage() {
  const [show, setShow] = useState(false);
  const [selected, setSelected] = useState<Warehouse>();
  const q = useQuery({ queryKey: ["warehouses"], queryFn: () => masterDataApi.warehouses() });

  return (
    <section>
      <PageTitle
        title="Warehouses"
        action={
          <Button onClick={() => setShow(v => !v)}>
            <Plus size={16} />
            {show ? "Close form" : "New warehouse"}
          </Button>
        }
      />
      {show && <WarehouseForm onComplete={() => { setShow(false); q.refetch(); }} />}
      {q.isLoading ? (
        <Loading />
      ) : q.isError ? (
        <ErrorBanner message={errorMessage(q.error)} />
      ) : !q.data?.content.length ? (
        <Empty title="No warehouses found" />
      ) : (
        <Panel>
          <div className="overflow-x-auto">
            <table className="w-full text-left">
              <thead className="text-xs uppercase text-muted">
                <tr>
                  <th className="py-2">Code</th>
                  <th>Name</th>
                  <th>Type</th>
                  <th>Status</th>
                  <th></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {q.data.content.map(w => (
                  <tr key={w.id} className="hover:bg-surface-soft/40">
                    <td className="py-3 font-mono font-medium text-ink">{w.code}</td>
                    <td>
                      <strong className="text-ink">{w.name}</strong>
                      <br />
                      <span className="text-xs text-muted">{w.address}</span>
                    </td>
                    <td className="text-sm">{w.type.replaceAll("_", " ")}</td>
                    <td><StatusBadge status={w.status} /></td>
                    <td className="text-right">
                      <button onClick={() => setSelected(w)} className="text-sm font-medium text-blue hover:underline">
                        Storage locations
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Panel>
      )}
      {selected && <StorageLocations warehouse={selected} close={() => setSelected(undefined)} />}
    </section>
  );
}

function WarehouseForm({ onComplete }: { onComplete: () => void }) {
  const [error, setError] = useState<unknown>();
  const m = useMutation({
    mutationFn: masterDataApi.createWarehouse,
    onSuccess: onComplete,
    onError: setError
  });

  function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const d = new FormData(e.currentTarget);
    m.mutate({
      code: String(d.get("code")),
      name: String(d.get("name")),
      type: String(d.get("type")),
      address: String(d.get("address")),
      contactPhone: String(d.get("contactPhone")) || undefined,
      status: String(d.get("status"))
    });
  }

  return (
    <div className="mb-6">
      <Panel>
        <h2 className="mb-3 font-semibold text-ink">Register new warehouse</h2>
        <form onSubmit={submit} className="grid gap-3 md:grid-cols-3">
          <Field label="Code">
            <input required name="code" className="input" placeholder="e.g. WH-02" />
          </Field>
          <Field label="Name">
            <input required name="name" className="input" placeholder="e.g. Regional Depot 2" />
          </Field>
          <Field label="Type">
            <select name="type" className="input">
              <option value="CENTRAL_WAREHOUSE">CENTRAL_WAREHOUSE</option>
              <option value="DISTRIBUTION_STORE">DISTRIBUTION_STORE</option>
            </select>
          </Field>
          <Field label="Address">
            <input required name="address" className="input" placeholder="Full address" />
          </Field>
          <Field label="Phone">
            <input name="contactPhone" className="input" placeholder="+1..." />
          </Field>
          <Field label="Status">
            <select name="status" className="input">
              <option value="ACTIVE">ACTIVE</option>
              <option value="INACTIVE">INACTIVE</option>
            </select>
          </Field>
          <div className="md:col-span-3">
            <FormError error={error} />
            <Button disabled={m.isPending}>
              <Save size={16} />
              {m.isPending ? "Creating…" : "Create warehouse"}
            </Button>
          </div>
        </form>
      </Panel>
    </div>
  );
}

function StorageLocations({ warehouse, close }: { warehouse: Warehouse; close: () => void }) {
  const q = useQuery({
    queryKey: ["storage", warehouse.id],
    queryFn: () => masterDataApi.storageLocations(warehouse.id)
  });
  const [error, setError] = useState<unknown>();
  const qc = useQueryClient();
  const m = useMutation({
    mutationFn: (body: { zone: string; rack: string; shelf: string; binCode: string }) =>
      masterDataApi.createStorageLocation(warehouse.id, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["storage", warehouse.id] }),
    onError: setError
  });

  function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const d = new FormData(e.currentTarget);
    m.mutate({
      zone: String(d.get("zone")),
      rack: String(d.get("rack")),
      shelf: String(d.get("shelf")),
      binCode: String(d.get("binCode"))
    });
    e.currentTarget.reset();
  }

  return (
    <div className="mt-6">
      <Panel>
        <div className="mb-3 flex items-center justify-between">
          <h2 className="font-semibold text-ink">Storage locations · {warehouse.name} ({warehouse.code})</h2>
          <button onClick={close} aria-label="Close storage locations" className="text-muted hover:text-ink">
            <X size={18} />
          </button>
        </div>
        {q.isLoading ? (
          <Loading />
        ) : !q.data?.length ? (
          <p className="mb-4 text-sm text-muted">No storage bins created yet.</p>
        ) : (
          <ul className="mb-4 grid gap-2 sm:grid-cols-3">
            {q.data?.map(s => (
              <li key={s.id} className="rounded border border-border bg-surface-soft p-2 font-mono text-sm">
                {s.binCode} <span className="text-xs text-muted">({s.zone}-{s.rack}-{s.shelf})</span>
              </li>
            ))}
          </ul>
        )}
        <form onSubmit={submit} className="grid gap-2 md:grid-cols-5">
          <input required className="input" name="zone" placeholder="Zone (e.g. A)" />
          <input required className="input" name="rack" placeholder="Rack (e.g. 01)" />
          <input required className="input" name="shelf" placeholder="Shelf (e.g. 02)" />
          <input required className="input" name="binCode" placeholder="Bin code (e.g. BIN-A-01-02)" />
          <Button disabled={m.isPending}>
            <Plus size={16} />
            {m.isPending ? "Adding…" : "Add bin"}
          </Button>
        </form>
        <FormError error={error} />
      </Panel>
    </div>
  );
}

export function SuppliersPage() {
  const [show, setShow] = useState(false);
  const q = useQuery({ queryKey: ["suppliers"], queryFn: () => masterDataApi.suppliers() });

  return (
    <section>
      <PageTitle
        title="Suppliers"
        action={
          <Button onClick={() => setShow(v => !v)}>
            <Plus size={16} />
            {show ? "Close form" : "New supplier"}
          </Button>
        }
      />
      {show && <SupplierForm onComplete={() => { setShow(false); q.refetch(); }} />}
      {q.isLoading ? (
        <Loading />
      ) : q.isError ? (
        <ErrorBanner message={errorMessage(q.error)} />
      ) : !q.data?.content.length ? (
        <Empty title="No suppliers found" />
      ) : (
        <Panel>
          <div className="overflow-x-auto">
            <table className="w-full text-left">
              <thead className="text-xs uppercase text-muted">
                <tr>
                  <th className="py-2">Code</th>
                  <th>Name</th>
                  <th>Contact</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {q.data.content.map(s => (
                  <tr key={s.id} className="hover:bg-surface-soft/40">
                    <td className="py-3 font-mono font-medium text-ink">{s.code}</td>
                    <td className="font-medium text-ink">{s.name}</td>
                    <td className="text-sm text-muted">{s.contactEmail ?? s.contactPhone ?? "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Panel>
      )}
    </section>
  );
}

function SupplierForm({ onComplete }: { onComplete: () => void }) {
  const [error, setError] = useState<unknown>();
  const m = useMutation({
    mutationFn: masterDataApi.createSupplier,
    onSuccess: onComplete,
    onError: setError
  });

  function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const d = new FormData(e.currentTarget);
    m.mutate({
      name: String(d.get("name")),
      code: String(d.get("code")),
      contactEmail: String(d.get("contactEmail")) || undefined,
      contactPhone: String(d.get("contactPhone")) || undefined,
      address: String(d.get("address")) || undefined
    });
  }

  return (
    <div className="mb-6">
      <Panel>
        <h2 className="mb-3 font-semibold text-ink">Register new supplier</h2>
        <form onSubmit={submit} className="grid gap-3 md:grid-cols-3">
          <Field label="Name">
            <input required name="name" className="input" placeholder="Supplier name" />
          </Field>
          <Field label="Code">
            <input required name="code" className="input" placeholder="e.g. SUP-001" />
          </Field>
          <Field label="Email">
            <input type="email" name="contactEmail" className="input" placeholder="orders@supplier.com" />
          </Field>
          <Field label="Phone">
            <input name="contactPhone" className="input" placeholder="+1..." />
          </Field>
          <Field label="Address">
            <input name="address" className="input" placeholder="Supplier location" />
          </Field>
          <div className="md:col-span-3">
            <FormError error={error} />
            <Button disabled={m.isPending}>
              <Save size={16} />
              {m.isPending ? "Creating…" : "Create supplier"}
            </Button>
          </div>
        </form>
      </Panel>
    </div>
  );
}