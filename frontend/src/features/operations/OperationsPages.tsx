import { FormEvent, useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Barcode,
  Bell,
  Check,
  ClipboardList,
  PackageCheck,
  Plus,
  Printer,
  ScanLine,
  Send,
  Truck,
  Camera,
  RefreshCw,
  Search,
  X
} from "lucide-react";
import { operationsApi } from "../../services/operationsApi";
import { masterDataApi } from "../../services/masterDataApi";
import { ErrorBanner, Empty, Loading } from "../../components/feedback/States";
import { StatusBadge } from "../../components/ui/StatusBadge";
import { errorMessage } from "../../utils/errors";
import type { Shipment, Transfer } from "../../types/api";

const Button = ({ children, ...p }: React.ButtonHTMLAttributes<HTMLButtonElement>) => (
  <button
    {...p}
    className={`inline-flex min-h-10 items-center justify-center gap-2 rounded-lg bg-ink px-4 text-sm font-semibold text-canvas shadow-xs transition hover:opacity-90 active:scale-[0.98] disabled:bg-surface-strong disabled:text-muted disabled:border disabled:border-border disabled:opacity-85 disabled:cursor-not-allowed ${
      p.className ?? ""
    }`}
  >
    {children}
  </button>
);

const Field = ({ label, children }: { label: string; children: React.ReactNode }) => (
  <label className="grid gap-1 text-xs font-semibold text-ink">
    <span>{label}</span>
    {children}
  </label>
);

const Error = ({ error }: { error: unknown }) =>
  error ? <ErrorBanner message={errorMessage(error)} /> : null;

export function TransfersPage() {
  const [transfer, setTransfer] = useState<Transfer>();
  const [error, setError] = useState<unknown>();
  const [showCreate, setShowCreate] = useState(false);
  const [destination, setDestination] = useState("");
  const [items, setItems] = useState([{ medicineId: "", quantity: 1 }]);

  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<"ALL" | "PENDING" | "ACTIVE" | "COMPLETED" | "CANCELLED">("ALL");
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);

  const qc = useQueryClient();
  const warehouses = useQuery({ queryKey: ["warehouses"], queryFn: () => masterDataApi.warehouses() });
  const medicines = useQuery({ queryKey: ["medicines"], queryFn: () => masterDataApi.medicines() });
  const transfersList = useQuery({ queryKey: ["transfersList"], queryFn: () => operationsApi.transfers() });

  const create = useMutation({
    mutationFn: (body: unknown) => operationsApi.createTransfer(body),
    onSuccess: t => {
      setTransfer(t);
      setError(undefined);
      setShowCreate(false);
      qc.invalidateQueries({ queryKey: ["transfersList"] });
    },
    onError: setError
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    create.mutate({
      destinationWarehouseId: destination,
      items,
      notes: (new FormData(e.currentTarget as HTMLFormElement).get("notes") as string) || undefined
    });
  }

  const warehouseMap = useMemo(() => {
    const map = new Map<string, { code: string; name: string }>();
    if (warehouses.data?.content) {
      for (const w of warehouses.data.content) {
        map.set(w.id, { code: w.code, name: w.name });
      }
    }
    return map;
  }, [warehouses.data]);

  const allTransfers = transfersList.data?.content ?? [];

  // Summary Metrics Counts
  const metrics = useMemo(() => {
    const total = allTransfers.length;
    const pending = allTransfers.filter(t => t.status === "REQUESTED" || t.status === "APPROVED").length;
    const active = allTransfers.filter(t => ["ALLOCATED", "PICKED", "PACKED", "DISPATCHED", "IN_TRANSIT"].includes(t.status)).length;
    const completed = allTransfers.filter(t => t.status === "COMPLETED" || t.status === "RECEIVED").length;
    return { total, pending, active, completed };
  }, [allTransfers]);

  // Filtered Transfers
  const filteredTransfers = useMemo(() => {
    return allTransfers.filter(t => {
      if (statusFilter === "PENDING" && !["REQUESTED", "APPROVED"].includes(t.status)) return false;
      if (statusFilter === "ACTIVE" && !["ALLOCATED", "PICKED", "PACKED", "DISPATCHED", "IN_TRANSIT"].includes(t.status)) return false;
      if (statusFilter === "COMPLETED" && !["COMPLETED", "RECEIVED"].includes(t.status)) return false;
      if (statusFilter === "CANCELLED" && t.status !== "CANCELLED") return false;

      if (!search.trim()) return true;
      const q = search.toLowerCase();
      const src = warehouseMap.get(t.sourceWarehouseId ?? "")?.name.toLowerCase() ?? "";
      const dst = warehouseMap.get(t.destinationWarehouseId)?.name.toLowerCase() ?? "";
      return (
        t.transferNumber.toLowerCase().includes(q) ||
        src.includes(q) ||
        dst.includes(q) ||
        t.status.toLowerCase().includes(q)
      );
    });
  }, [allTransfers, statusFilter, search, warehouseMap]);

  // Pagination
  const totalItems = filteredTransfers.length;
  const totalPages = Math.max(1, Math.ceil(totalItems / pageSize));
  const currentPage = Math.min(page, totalPages - 1);
  const paginatedTransfers = useMemo(() => {
    const start = Math.max(0, currentPage) * pageSize;
    return filteredTransfers.slice(start, start + pageSize);
  }, [filteredTransfers, currentPage, pageSize]);

  const pageStart = totalItems === 0 ? 0 : Math.max(0, currentPage) * pageSize + 1;
  const pageEnd = Math.min((Math.max(0, currentPage) + 1) * pageSize, totalItems);

  function getActionInfo(status: string) {
    switch (status) {
      case "REQUESTED":
        return { label: "Allocate (FEFO) →", primary: true };
      case "APPROVED":
        return { label: "Allocate Stock →", primary: true };
      case "ALLOCATED":
        return { label: "Pick & Dispatch →", primary: true };
      case "PICKED":
      case "PACKED":
        return { label: "Dispatch Shipment →", primary: true };
      case "DISPATCHED":
      case "IN_TRANSIT":
        return { label: "Receive Stock →", primary: true };
      case "COMPLETED":
      case "RECEIVED":
        return { label: "View Manifest & Audit →", primary: false };
      case "CANCELLED":
        return { label: "View Audit Log →", primary: false };
      default:
        return { label: "Manage Workbench →", primary: false };
    }
  }

  if (warehouses.isLoading || medicines.isLoading) return <Loading />;

  return (
    <section className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-border pb-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-ink">Stock transfers & FEFO allocation</h1>
          <p className="mt-1 text-xs text-muted">
            Request stock transfers, allocate earliest-expiring inventory via FEFO, pick, pack, and track inter-warehouse dispatches.
          </p>
        </div>
        <Button onClick={() => setShowCreate(v => !v)}>
          <Plus size={16} />
          {showCreate ? "Close form" : "New transfer request"}
        </Button>
      </div>

      {/* KPI Overview Cards */}
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <div className="rounded-xl border border-border bg-canvas p-3.5 shadow-xs">
          <span className="text-[11px] font-medium text-muted uppercase tracking-wider">Total Transfers</span>
          <p className="mt-1 text-2xl font-bold text-ink">{metrics.total}</p>
        </div>
        <div className="rounded-xl border border-border bg-canvas p-3.5 shadow-xs">
          <span className="text-[11px] font-medium text-warning uppercase tracking-wider">Pending FEFO</span>
          <p className="mt-1 text-2xl font-bold text-ink">{metrics.pending}</p>
        </div>
        <div className="rounded-xl border border-border bg-canvas p-3.5 shadow-xs">
          <span className="text-[11px] font-medium text-blue uppercase tracking-wider">Active In-Transit</span>
          <p className="mt-1 text-2xl font-bold text-ink">{metrics.active}</p>
        </div>
        <div className="rounded-xl border border-border bg-canvas p-3.5 shadow-xs">
          <span className="text-[11px] font-medium text-success uppercase tracking-wider">Completed Fulfillments</span>
          <p className="mt-1 text-2xl font-bold text-ink">{metrics.completed}</p>
        </div>
      </div>

      {showCreate && (
        <form onSubmit={submit} className="rounded-xl border border-border bg-canvas p-5 shadow-xs">
          <div className="mb-4 border-b border-border pb-3">
            <h2 className="text-base font-semibold text-ink">Create transfer request</h2>
            <p className="text-xs text-muted">Initialize an inter-facility stock movement with atomic FEFO reservation.</p>
          </div>
          <div className="grid gap-3 md:grid-cols-2">
            <Field label="Destination warehouse">
              <select required name="destinationWarehouseId" value={destination}
                onChange={e => setDestination(e.target.value)}
                className="input text-xs"
              >
                <option value="">Select destination facility…</option>
                {warehouses.data?.content
                  .filter(x => x.status === "ACTIVE")
                  .map(x => (
                    <option key={x.id} value={x.id}>
                      {x.code} — {x.name} ({x.type.replaceAll("_", " ")})
                    </option>
                  ))}
              </select>
            </Field>
            <Field label="Notes & Priority">
              <input name="notes" className="input text-xs" placeholder="e.g. Urgent dispensary restock" />
            </Field>
          </div>
          <div className="mt-4">
            <div className="mb-2 flex items-center justify-between">
              <h3 className="text-xs font-semibold text-ink uppercase tracking-wider">Requested medicines</h3>
              <button
                type="button"
                className="text-xs font-semibold text-blue hover:underline"
                onClick={() => setItems(i => [...i, { medicineId: "", quantity: 1 }])}
              >
                <Plus className="mr-1 inline" size={14} />
                Add item
              </button>
            </div>
            {items.map((item, index) => (
              <div className="mb-2 grid grid-cols-[1fr_140px_auto] gap-2" key={index}>
                <select required name="medicineId" value={item.medicineId}
                  onChange={e =>
                    setItems(all =>
                      all.map((x, i) => (i === index ? { ...x, medicineId: e.target.value } : x))
                    )
                  }
                  className="input text-xs"
                >
                  <option value="">Select active medicine formulation…</option>
                  {medicines.data?.content
                    .filter(x => x.status === "ACTIVE")
                    .map(x => (
                      <option key={x.id} value={x.id}>
                        {x.sku} — {x.genericName}
                      </option>
                    ))}
                </select>
                <input
                  aria-label="Requested quantity"
                  className="input text-xs font-mono"
                  min="1"
                  type="number"
                  placeholder="Quantity"
                  value={item.quantity}
                  onChange={e =>
                    setItems(all =>
                      all.map((x, i) => (i === index ? { ...x, quantity: Number(e.target.value) } : x))
                    )
                  }
                />
                <button
                  aria-label="Remove item"
                  className="rounded px-2.5 text-danger hover:bg-danger-bg/50 disabled:opacity-40"
                  type="button"
                  disabled={items.length === 1}
                  onClick={() => setItems(all => all.filter((_, i) => i !== index))}
                >
                  ×
                </button>
              </div>
            ))}
          </div>
          <Error error={error} />
          <div className="mt-4 pt-2">
            <Button type="submit" disabled={create.isPending}>
              <Plus size={16} />
              {create.isPending ? "Submitting transfer…" : "Submit transfer request"}
            </Button>
          </div>
        </form>
      )}

      {/* Control Filter Bar */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-1 flex-wrap items-center gap-3">
          <div className="relative flex flex-1 min-w-[280px] max-w-md items-center">
            <Search size={16} className="pointer-events-none absolute left-3.5 text-muted z-10" />
            <input
              value={search}
              onChange={e => {
                setSearch(e.target.value);
                setPage(0);
              }}
              className="input search-input h-10 text-xs w-full"
              placeholder="Search transfer # or warehouse…"
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

          <div className="flex h-10 items-center rounded-lg border border-border bg-surface-soft p-1 text-xs shrink-0">
            {(
              [
                { id: "ALL", label: "All transfers" },
                { id: "PENDING", label: "Pending allocation" },
                { id: "ACTIVE", label: "In transit" },
                { id: "COMPLETED", label: "Completed" }
              ] as const
            ).map(tab => {
              const isActive = statusFilter === tab.id;
              return (
                <button
                  key={tab.id}
                  onClick={() => {
                    setStatusFilter(tab.id);
                    setPage(0);
                  }}
                  className={`h-full px-3 rounded-md text-xs font-semibold transition flex items-center justify-center ${
                    isActive
                      ? "bg-canvas text-ink shadow-xs border border-border/80"
                      : "text-muted hover:text-ink hover:bg-canvas/50"
                  }`}
                >
                  {tab.label}
                </button>
              );
            })}
          </div>
        </div>

        <div className="flex h-10 items-center rounded-lg border border-border bg-surface-soft px-3.5 text-xs text-muted shrink-0">
          Showing&nbsp;<strong className="text-ink">{pageStart}–{pageEnd}</strong>&nbsp;of&nbsp;<strong className="text-ink">{totalItems}</strong>&nbsp;transfers
        </div>
      </div>

      <div className="rounded-xl border border-border bg-canvas shadow-xs overflow-hidden">
        {transfersList.isLoading ? (
          <Loading />
        ) : !paginatedTransfers.length ? (
          <div className="p-8 text-center">
            <Empty title="No stock transfers found" />
            {(search || statusFilter !== "ALL") && (
              <button
                onClick={() => {
                  setSearch("");
                  setStatusFilter("ALL");
                }}
                className="mt-3 rounded-lg border border-border bg-surface-soft px-3 py-1 text-xs font-semibold text-ink hover:bg-canvas"
              >
                Reset search filters
              </button>
            )}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="border-b border-border bg-surface-soft/60 text-[11px] font-semibold uppercase tracking-wider text-muted">
                <tr>
                  <th className="px-4 py-3 w-[160px]">Transfer #</th>
                  <th className="px-4 py-3 min-w-[280px]">Source → Destination Route</th>
                  <th className="px-4 py-3 w-[120px]">Status</th>
                  <th className="px-4 py-3 text-right w-[180px]">Operational Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {paginatedTransfers.map(t => {
                  const src = warehouseMap.get(t.sourceWarehouseId ?? "");
                  const dst = warehouseMap.get(t.destinationWarehouseId);
                  const action = getActionInfo(t.status);
                  const isSelected = transfer?.id === t.id;

                  return (
                    <tr
                      key={t.id}
                      className={`transition hover:bg-surface-soft/40 ${
                        isSelected ? "bg-surface-soft/80 font-medium" : ""
                      }`}
                    >
                      <td className="px-4 py-3 font-mono font-semibold text-ink">
                        {t.transferNumber}
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex flex-wrap items-center gap-1.5 text-xs font-medium text-ink">
                          <span className="rounded bg-surface-soft px-1.5 py-0.5 font-mono text-[11px] font-bold text-ink">
                            {src?.code ?? "CW01"}
                          </span>
                          <span className="text-muted text-[11px]">({src?.name ?? "Central Warehouse"})</span>
                          <span className="text-muted font-bold">→</span>
                          <span className="rounded bg-surface-soft px-1.5 py-0.5 font-mono text-[11px] font-bold text-ink">
                            {dst?.code ?? "DS01"}
                          </span>
                          <span className="text-muted text-[11px]">({dst?.name ?? "Distribution Store"})</span>
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        <StatusBadge status={t.status} />
                      </td>
                      <td className="px-4 py-3 text-right">
                        <button
                          onClick={() => {
                            operationsApi.transfer(t.id).then(setTransfer);
                          }}
                          className={`inline-flex items-center gap-1 rounded-md px-2.5 py-1 text-xs font-semibold shadow-xs transition ${
                            action.primary
                              ? "bg-ink text-canvas hover:opacity-90"
                              : "border border-border bg-canvas text-ink hover:bg-surface-soft"
                          }`}
                        >
                          <span>{action.label}</span>
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        {/* Pagination Bar */}
        {totalItems > 0 && (
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
                className="rounded border border-border bg-canvas px-2.5 py-1 text-xs font-medium text-ink shadow-xs hover:bg-surface-soft disabled:opacity-40 disabled:cursor-not-allowed"
                aria-label="Previous page"
              >
                Prev
              </button>
              <span className="font-mono text-muted">
                Page <strong className="text-ink">{currentPage + 1}</strong> of{" "}
                <strong className="text-ink">{totalPages}</strong>
              </span>
              <button
                disabled={currentPage >= totalPages - 1}
                onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
                className="rounded border border-border bg-canvas px-2.5 py-1 text-xs font-medium text-ink shadow-xs hover:bg-surface-soft disabled:opacity-40 disabled:cursor-not-allowed"
                aria-label="Next page"
              >
                Next
              </button>
            </div>
          </div>
        )}
      </div>

      {transfer && <TransferWorkbench transfer={transfer} onChange={setTransfer} />}
    </section>
  );
}

function TransferWorkbench({
  transfer,
  onChange
}: {
  transfer: Transfer;
  onChange: (t: Transfer) => void;
}) {
  const [error, setError] = useState<unknown>();
  const [pick, setPick] = useState(
    transfer.items.filter(x => x.batchId).map(x => ({ batchId: x.batchId!, quantity: x.allocatedQuantity }))
  );

  const qc = useQueryClient();

  useEffect(() => {
    setPick(
      transfer.items
        .filter(x => x.batchId)
        .map(x => ({ batchId: x.batchId!, quantity: x.allocatedQuantity }))
    );
  }, [transfer]);

  const change = (f: () => Promise<Transfer>) => {
    setError(undefined);
    f()
      .then(res => {
        onChange(res);
        qc.invalidateQueries({ queryKey: ["transfersList"] });
      })
      .catch(setError);
  };

  return (
    <div className="rounded-lg border border-border bg-canvas p-5 shadow-sm">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-lg font-semibold text-ink">{transfer.transferNumber}</h2>
            <StatusBadge status={transfer.status} />
          </div>
          <p className="font-mono text-xs text-muted">ID: {transfer.id}</p>
        </div>
      </div>

      <div className="mb-6 flex flex-wrap gap-2 text-xs">
        {["REQUESTED", "APPROVED", "ALLOCATED", "PICKED", "PACKED", "DISPATCHED", "RECEIVED"].map(
          (st, idx) => {
            const stepOrder = [
              "REQUESTED",
              "APPROVED",
              "ALLOCATED",
              "PICKED",
              "PACKED",
              "DISPATCHED",
              "RECEIVED",
              "COMPLETED"
            ];
            const currentIdx = stepOrder.indexOf(transfer.status);
            const isDone = currentIdx >= idx;
            const isCurrent = transfer.status === st;

            return (
              <span
                key={st}
                className={`rounded px-2.5 py-1 font-medium ${
                  isCurrent
                    ? "bg-ink text-white"
                    : isDone
                    ? "bg-success-bg text-success border border-success-border"
                    : "bg-surface text-muted"
                }`}
              >
                {idx + 1}. {st}
              </span>
            );
          }
        )}
      </div>

      <div className="mb-4 overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-border bg-surface-soft text-xs uppercase text-muted">
            <tr>
              <th className="p-2.5">Batch</th>
              <th className="p-2.5 text-right">Requested</th>
              <th className="p-2.5 text-right">Allocated (FEFO)</th>
              <th className="p-2.5 text-right">Picked</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {transfer.items.map((x, i) => (
              <tr key={`${x.batchId}-${i}`} className="hover:bg-surface-soft/40">
                <td className="p-2.5 font-mono text-xs text-ink">{x.batchId ?? "FEFO pending allocation"}</td>
                <td className="p-2.5 text-right font-mono font-medium text-ink">{x.requestedQuantity}</td>
                <td className="p-2.5 text-right font-mono font-medium text-success">{x.allocatedQuantity}</td>
                <td className="p-2.5 text-right font-mono text-muted">{x.pickedQuantity}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="flex flex-wrap items-center gap-3">
        {transfer.status === "REQUESTED" && (
          <Button onClick={() => change(() => operationsApi.approveTransfer(transfer.id))}>
            <Check size={16} />
            Approve transfer
          </Button>
        )}
        {transfer.status === "APPROVED" && (
          <Button onClick={() => change(() => operationsApi.allocateTransfer(transfer.id))}>
            <ClipboardList size={16} />
            Run FEFO allocation
          </Button>
        )}
        {transfer.status === "ALLOCATED" && (
          <form
            onSubmit={e => {
              e.preventDefault();
              change(() => operationsApi.pickTransfer(transfer.id, { items: pick }));
            }}
            className="flex flex-wrap items-center gap-3"
          >
            <span className="text-xs text-muted">Confirm picking verified batches:</span>
            <Button>
              <PackageCheck size={16} />
              Confirm pick
            </Button>
          </form>
        )}
        {transfer.status === "PICKED" && (
          <Button onClick={() => change(() => operationsApi.packTransfer(transfer.id))}>
            <PackageCheck size={16} />
            Pack transfer
          </Button>
        )}

        {["REQUESTED", "APPROVED", "ALLOCATED"].includes(transfer.status) && (
          <button
            className="inline-flex min-h-10 items-center rounded border border-danger/40 px-3 text-sm font-medium text-danger hover:bg-danger-bg"
            onClick={() => {
              const reason = window.prompt("Cancellation reason (required):", "Operator cancelled");
              if (reason) change(() => operationsApi.cancelTransfer(transfer.id, reason));
            }}
          >
            Cancel transfer
          </button>
        )}
      </div>
      <Error error={error} />
    </div>
  );
}
export function ShipmentsPage() {
  const [shipment, setShipment] = useState<Shipment>();
  const [error, setError] = useState<unknown>();
  const [showCreate, setShowCreate] = useState(false);

  const qc = useQueryClient();
  const shipmentsList = useQuery({ queryKey: ["shipmentsList"], queryFn: () => operationsApi.shipments() });
  const transfersList = useQuery({ queryKey: ["transfersList"], queryFn: () => operationsApi.transfers() });

  const packedTransfers = useMemo(
    () => transfersList.data?.content.filter(t => t.status === "PACKED") ?? [],
    [transfersList.data]
  );

  const create = useMutation({
    mutationFn: (body: unknown) => operationsApi.createShipment(body),
    onSuccess: s => {
      setShipment(s);
      setError(undefined);
      setShowCreate(false);
      qc.invalidateQueries({ queryKey: ["shipmentsList"] });
    },
    onError: setError
  });

  function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const d = new FormData(e.currentTarget);
    create.mutate({
      transferId: d.get("transferId"),
      carrierName: d.get("carrierName"),
      trackingNumber: d.get("trackingNumber"),
      driverName: d.get("driverName") || undefined,
      driverPhone: d.get("driverPhone") || undefined,
      vehicleNumber: d.get("vehicleNumber") || undefined,
      estimatedArrival: new Date(String(d.get("estimatedArrival"))).toISOString()
    });
  }

  return (
    <section className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-ink">Shipments & freight dispatch</h1>
          <p className="text-muted">
            Create shipment manifests for packed transfers, dispatch carriers, and receive inventory at destination.
          </p>
        </div>
        <Button onClick={() => setShowCreate(v => !v)}>
          <Truck size={16} />
          {showCreate ? "Close form" : "Create shipment manifest"}
        </Button>
      </div>

      {showCreate && (
        <form onSubmit={submit} className="grid gap-3 rounded-lg border border-border bg-canvas p-5 shadow-sm md:grid-cols-2">
          <h2 className="mb-2 font-semibold text-ink md:col-span-2">New shipment transport manifest</h2>
          <Field label="Packed stock transfer">
            {packedTransfers.length > 0 ? (
              <select required name="transferId" className="input">
                <option value="">Select packed transfer</option>
                {packedTransfers.map(t => (
                  <option key={t.id} value={t.id}>
                    {t.transferNumber} (Destination: {t.destinationWarehouseId.slice(0, 8)})
                  </option>
                ))}
              </select>
            ) : (
              <input
                required
                name="transferId"
                className="input"
                placeholder="Enter packed transfer UUID"
              />
            )}
          </Field>
          <Field label="Carrier name">
            <input required name="carrierName" className="input" placeholder="e.g. PharmaLogistics Express" />
          </Field>
          <Field label="Carrier tracking number">
            <input required name="trackingNumber" className="input" placeholder="e.g. TRK-2026-9921" />
          </Field>
          <Field label="Estimated arrival">
            <input
              required
              name="estimatedArrival"
              type="datetime-local"
              className="input"
              defaultValue={new Date(Date.now() + 86400000).toISOString().slice(0, 16)}
            />
          </Field>
          <Field label="Driver name">
            <input name="driverName" className="input" placeholder="e.g. Tariq Mansoor" />
          </Field>
          <Field label="Vehicle plate number">
            <input name="vehicleNumber" className="input" placeholder="e.g. MED-8821" />
          </Field>
          <Field label="Driver phone">
            <input name="driverPhone" className="input" placeholder="+1..." />
          </Field>
          <div className="md:col-span-2">
            <Error error={error} />
            <Button disabled={create.isPending} className="mt-2">
              <Truck size={16} />
              {create.isPending ? "Creating…" : "Create shipment"}
            </Button>
          </div>
        </form>
      )}

      <div className="rounded-lg border border-border bg-canvas shadow-sm">
        <div className="border-b border-border p-4">
          <h2 className="text-base font-semibold text-ink">All shipments</h2>
        </div>
        {shipmentsList.isLoading ? (
          <Loading />
        ) : !shipmentsList.data?.content.length ? (
          <Empty title="No shipments recorded yet" />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-border bg-surface-soft text-xs uppercase text-muted">
                <tr>
                  <th className="p-3">Shipment #</th>
                  <th className="p-3">Tracking #</th>
                  <th className="p-3">Carrier</th>
                  <th className="p-3">Status</th>
                  <th className="p-3 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {shipmentsList.data.content.map(s => (
                  <tr
                    key={s.id}
                    className={`hover:bg-surface-soft/50 ${
                      shipment?.id === s.id ? "bg-surface-soft font-medium" : ""
                    }`}
                  >
                    <td className="p-3 font-mono font-medium text-ink">{s.shipmentNumber}</td>
                    <td className="p-3 font-mono text-xs text-muted">{s.trackingNumber}</td>
                    <td className="p-3 text-muted">{s.carrierName}</td>
                    <td className="p-3">
                      <StatusBadge status={s.status} />
                    </td>
                    <td className="p-3 text-right">
                      <button
                        onClick={() => {
                          operationsApi.shipment(s.id).then(setShipment);
                        }}
                        className="text-sm font-medium text-blue hover:underline"
                      >
                        Workbench
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {shipment && <ShipmentWorkbench shipment={shipment} onError={setError} />}
    </section>
  );
}

function ShipmentWorkbench({
  shipment,
  onError
}: {
  shipment: Shipment;
  onError: (e: unknown) => void;
}) {
  const [status, setStatus] = useState(shipment.status);
  const [transferId] = useState(shipment.transferId);
  const [storageLocationId, setStorageLocationId] = useState("");
  const [receiveSuccess, setReceiveSuccess] = useState(false);
  const [localError, setLocalError] = useState<unknown>();

  const qc = useQueryClient();

  const receiveItems = useMemo(
    () =>
      shipment.items
        .filter(x => x.batchId)
        .map(x => ({
          batchId: x.batchId!,
          receivedQuantity: x.quantity,
          damagedQuantity: 0
        })),
    [shipment]
  );

  const dispatch = () => {
    setLocalError(undefined);
    operationsApi
      .dispatch(transferId)
      .then(s => {
        setStatus(s.status);
        qc.invalidateQueries({ queryKey: ["shipmentsList"] });
      })
      .catch(e => {
        setLocalError(e);
        onError(e);
      });
  };

  const receive = () => {
    setLocalError(undefined);
    operationsApi
      .receive(transferId, { storageLocationId, items: receiveItems })
      .then(() => {
        setStatus("RECEIVED");
        setReceiveSuccess(true);
        qc.invalidateQueries({ queryKey: ["shipmentsList"] });
        qc.invalidateQueries({ queryKey: ["balances"] });
      })
      .catch(e => {
        setLocalError(e);
        onError(e);
      });
  };

  return (
    <div className="rounded-lg border border-border bg-canvas p-5 shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-lg font-semibold text-ink">{shipment.shipmentNumber}</h2>
            <StatusBadge status={status} />
          </div>
          <p className="text-xs text-muted">Tracking: {shipment.trackingNumber}</p>
        </div>
      </div>

      {receiveSuccess && (
        <div role="status" className="my-4 rounded border border-success-border bg-success-bg p-4 text-success">
          <strong>Receipt Confirmed!</strong> Shipment received and destination inventory updated.
        </div>
      )}

      <div className="my-4">
        <h3 className="text-xs uppercase text-muted">Manifest items</h3>
        <ul className="mt-2 divide-y divide-border rounded border border-border bg-surface-soft p-3 text-sm">
          {shipment.items.map(x => (
            <li key={x.shipmentItemId} className="py-1 font-mono text-xs">
              Batch: {x.batchNumber ?? x.batchId} · Quantity: <strong>{x.quantity}</strong>
            </li>
          ))}
        </ul>
      </div>

      {status === "PREPARING" && (
        <Button onClick={dispatch}>
          <Send size={16} />
          Dispatch shipment
        </Button>
      )}

      {status === "DISPATCHED" && (
        <div className="space-y-3">
          <div className="flex flex-wrap items-end gap-3">
            <Field label="Destination storage location ID">
              <input
                value={storageLocationId}
                onChange={e => setStorageLocationId(e.target.value)}
                required
                className="input min-w-[280px]"
                placeholder="Enter destination storage bin ID"
              />
            </Field>
            <Button disabled={!storageLocationId} onClick={receive}>
              <Check size={16} />
              Confirm physical receipt
            </Button>
          </div>
        </div>
      )}

      {localError ? <Error error={localError} /> : null}
    </div>
  );
}
export function NotificationsPage() {
  const q = useQuery({ queryKey: ["notifications"], queryFn: operationsApi.notifications });
  const qc = useQueryClient();
  const read = useMutation({
    mutationFn: operationsApi.markNotificationRead,
    onSuccess: () => qc.invalidateQueries({ queryKey: ["notifications"] })
  });

  const evaluate = useMutation({
    mutationFn: operationsApi.evaluateNotifications,
    onSuccess: () => qc.invalidateQueries({ queryKey: ["notifications"] })
  });

  if (q.isLoading) return <Loading />;
  if (q.isError) return <ErrorBanner message={errorMessage(q.error)} />;

  return (
    <section className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-ink">Notification center</h1>
          <p className="text-muted">Unread operational alerts generated by server-side alert policies.</p>
        </div>
        <Button onClick={() => evaluate.mutate()} disabled={evaluate.isPending}>
          <RefreshCw size={16} className={evaluate.isPending ? "animate-spin" : ""} />
          Run alert scan
        </Button>
      </div>

      {!q.data?.length ? (
        <Empty title="You have no unread notifications" />
      ) : (
        <div className="grid gap-3">
          {q.data.map(n => {
            const isCritical = n.type.includes("CRITICAL") || n.type.includes("EXPIRED");
            const badgeClass = isCritical
              ? "border-danger/30 bg-danger-bg text-danger"
              : n.type.includes("DELAY") || n.type.includes("LOW")
              ? "border-warning/30 bg-warning-bg text-warning"
              : "border-info/30 bg-info-bg text-info";
            const badgeLabel = isCritical
              ? "Critical Expiry (≤30 Days)"
              : n.type.includes("DELAY")
              ? "Shipment Delayed"
              : n.type.includes("LOW")
              ? "Low Stock Threshold"
              : "Near Expiry (≤90 Days)";

            return (
              <article key={n.id} className="rounded-lg border border-border bg-canvas p-4 shadow-sm transition hover:bg-surface-soft/40">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <Bell size={16} className={isCritical ? "text-danger" : "text-warning"} />
                      <strong className="text-sm font-semibold text-ink">{n.title}</strong>
                      <span className={`inline-flex shrink-0 items-center whitespace-nowrap rounded-full border px-2 py-0.5 text-[11px] font-semibold ${badgeClass}`}>
                        {badgeLabel}
                      </span>
                    </div>
                    <p className="mt-2 text-xs leading-relaxed text-body">{n.message}</p>
                    <time className="mt-3 block font-mono text-[11px] text-muted">
                      {new Intl.DateTimeFormat("en-US", {
                        month: "short",
                        day: "numeric",
                        year: "numeric",
                        hour: "2-digit",
                        minute: "2-digit",
                        second: "2-digit",
                        hour12: true,
                        timeZoneName: "short"
                      }).format(new Date(n.createdAt))}
                    </time>
                  </div>
                  <button
                    className="inline-flex shrink-0 items-center rounded border border-border bg-surface-soft px-2.5 py-1 text-xs font-medium text-ink hover:bg-canvas"
                    onClick={() => read.mutate(n.id)}
                  >
                    Mark read
                  </button>
                </div>
              </article>
            );
          })}
        </div>
      )}
    </section>
  );
}

export function AuditPage() {
  const q = useQuery({ queryKey: ["audit"], queryFn: () => operationsApi.audit() });

  if (q.isLoading) return <Loading />;
  if (q.isError) return <ErrorBanner message={errorMessage(q.error)} />;

  return (
    <section className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-ink">Audit log</h1>
        <p className="text-muted">Immutable, server-generated operational history.</p>
      </div>

      {!q.data?.content.length ? (
        <Empty title="No audit records found" />
      ) : (
        <div className="overflow-x-auto rounded-lg border border-border bg-canvas shadow-sm">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-border bg-surface-soft text-xs uppercase text-muted">
              <tr>
                <th className="p-3">Timestamp</th>
                <th className="p-3">Action</th>
                <th className="p-3">Entity</th>
                <th className="p-3">Actor ID</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {q.data.content.map(x => (
                <tr key={x.id} className="hover:bg-surface-soft/40">
                  <td className="p-3 text-muted">{new Date(x.createdAt).toLocaleString()}</td>
                  <td className="p-3 font-semibold text-ink">{x.action}</td>
                  <td className="p-3 font-mono text-xs">{x.entityName}</td>
                  <td className="p-3 font-mono text-xs text-muted">{x.userId ?? "System"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

export function LabelsPage() {
  const [batchId, setBatchId] = useState("");
  const [kind, setKind] = useState<"qr" | "code128">("qr");

  const batches = useQuery({ queryKey: ["batchesList"], queryFn: () => masterDataApi.batches() });

  const q = useQuery({
    queryKey: ["label", kind, batchId],
    queryFn: () => (kind === "qr" ? operationsApi.qr(batchId) : operationsApi.code128(batchId)),
    enabled: !!batchId
  });

  return (
    <section className="max-w-xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-ink">Batch labels & barcodes</h1>
        <p className="text-muted">
          Generate an authenticated 2D QR code (containing SKU, batch, and expiry JSON) or 1D Code 128 barcode.
        </p>
      </div>

      <div className="space-y-3">
        <Field label="Select batch or enter ID">
          {batches.data?.content.length ? (
            <select
              value={batchId}
              onChange={e => setBatchId(e.target.value)}
              className="input"
              aria-label="Batch ID"
            >
              <option value="">Select a registered batch</option>
              {batches.data.content.map(b => (
                <option key={b.id} value={b.id}>
                  {b.batchNumber} (Expires: {b.expiryDate})
                </option>
              ))}
            </select>
          ) : (
            <input
              aria-label="Batch ID"
              value={batchId}
              onChange={e => setBatchId(e.target.value)}
              className="input"
              placeholder="Batch UUID"
            />
          )}
        </Field>

        <Field label="Barcode type">
          <select
            value={kind}
            onChange={e => setKind(e.target.value as "qr" | "code128")}
            className="input"
          >
            <option value="qr">2D QR code (Pharma JSON contract)</option>
            <option value="code128">1D Code 128</option>
          </select>
        </Field>
      </div>

      {q.isLoading && <Loading />}
      {q.isError && <ErrorBanner message={errorMessage(q.error)} />}

      {q.data && (
        <div className="mt-5 rounded-lg border border-border bg-canvas p-6 text-center shadow-sm print:border-0 print:p-0">
          <img
            className="mx-auto max-h-64 max-w-full"
            src={q.data.dataUri}
            alt={`${kind} label for batch ${batchId}`}
          />
          <p className="mt-3 font-mono text-xs text-muted">ID: {batchId}</p>
          <Button className="no-print mt-4" onClick={() => window.print()}>
            <Printer size={16} />
            Print label
          </Button>
        </div>
      )}
    </section>
  );
}

export function ScannerPage() {
  const [value, setValue] = useState("");
  const [scanned, setScanned] = useState("");
  const [cameraActive, setCameraActive] = useState(false);

  return (
    <section className="max-w-xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-ink">Barcode & QR scanner</h1>
        <p className="text-muted">
          Use a hardware wedge scanner, camera, or manual input to resolve batch and shipment identifiers.
        </p>
      </div>

      <div className="flex gap-2">
        <button
          onClick={() => setCameraActive(c => !c)}
          className="inline-flex min-h-10 items-center gap-2 rounded border border-border bg-canvas px-4 text-sm font-medium text-ink hover:bg-surface-soft"
        >
          <Camera size={16} />
          {cameraActive ? "Turn off camera" : "Activate camera scanner"}
        </button>
      </div>

      {cameraActive && (
        <div className="rounded-lg border border-border bg-surface-soft p-6 text-center text-sm text-muted">
          <p>Camera feed active. Align barcode or QR code within the viewfinder.</p>
        </div>
      )}

      <form
        onSubmit={e => {
          e.preventDefault();
          setScanned(value.trim());
        }}
        className="flex gap-2"
      >
        <input
          autoFocus
          value={value}
          onChange={e => setValue(e.target.value)}
          className="input"
          placeholder="Scan barcode or type identifier…"
          aria-label="Scan or enter a batch ID"
        />
        <Button type="submit">
          <ScanLine size={16} />
          Resolve
        </Button>
      </form>

      {scanned && (
        <div role="status" className="rounded border border-border bg-surface-soft p-4">
          <div className="flex items-center gap-2 text-ink">
            <Barcode size={20} />
            <strong className="text-sm">Scanned identifier</strong>
          </div>
          <p className="mt-2 break-all font-mono text-sm font-medium text-ink">{scanned}</p>
          <p className="mt-2 text-xs text-muted">
            Ready to use in receiving, batch label generation, or stock transfer verification.
          </p>
        </div>
      )}
    </section>
  );
}