import { FormEvent, useMemo, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import {
  AlertTriangle,
  ArrowRight,
  Boxes,
  CheckCircle2,
  Clock,
  Info,
  Layers,
  MapPin,
  PackagePlus,
  RefreshCw,
  Save,
  ShieldAlert,
  ShieldCheck,
  Truck,
  Warehouse
} from "lucide-react";
import { inventoryApi } from "../../services/inventoryApi";
import { masterDataApi } from "../../services/masterDataApi";
import { ErrorBanner, Loading } from "../../components/feedback/States";
import { errorMessage } from "../../utils/errors";
import type { InboundReceipt } from "../../types/api";

export function InboundReceivingPage() {
  const [supplierId, setSupplierId] = useState("");
  const [medicineId, setMedicineId] = useState("");
  const [warehouseId, setWarehouseId] = useState("");
  const [storageLocationId, setStorageLocationId] = useState("");
  const [batchNumber, setBatchNumber] = useState("");
  const [quantity, setQuantity] = useState("");
  const [manufacturingDate, setManufacturingDate] = useState("");
  const [expiryDate, setExpiryDate] = useState("");

  const [receipt, setReceipt] = useState<InboundReceipt | null>(null);
  const [error, setError] = useState<unknown>(null);

  const medicines = useQuery({ queryKey: ["medicines"], queryFn: () => masterDataApi.medicines() });
  const suppliers = useQuery({ queryKey: ["suppliers"], queryFn: () => masterDataApi.suppliers() });
  const warehouses = useQuery({ queryKey: ["warehouses"], queryFn: () => masterDataApi.warehouses() });
  const storage = useQuery({
    queryKey: ["storage", warehouseId],
    queryFn: () => masterDataApi.storageLocations(warehouseId),
    enabled: Boolean(warehouseId)
  });

  const mutation = useMutation({
    mutationFn: ({ body, key }: { body: unknown; key: string }) =>
      inventoryApi.inbound(body, key).then(r => r.data as InboundReceipt),
    onSuccess: r => {
      setReceipt(r);
      setError(null);
    },
    onError: err => {
      setError(err);
      setReceipt(null);
    }
  });

  // Selected item references for live preview
  const selectedMedicine = useMemo(
    () => medicines.data?.content.find(m => m.id === medicineId),
    [medicines.data, medicineId]
  );
  const selectedSupplier = useMemo(
    () => suppliers.data?.content.find(s => s.id === supplierId),
    [suppliers.data, supplierId]
  );
  const selectedWarehouse = useMemo(
    () => warehouses.data?.content.find(w => w.id === warehouseId),
    [warehouses.data, warehouseId]
  );
  const selectedStorage = useMemo(
    () => storage.data?.find(l => l.id === storageLocationId),
    [storage.data, storageLocationId]
  );

  // Shelf-life calculation
  const shelfLifeDays = useMemo(() => {
    if (!expiryDate) return null;
    const exp = new Date(expiryDate);
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const diffTime = exp.getTime() - today.getTime();
    return Math.ceil(diffTime / (1000 * 60 * 60 * 24));
  }, [expiryDate]);

  // Date relationship check
  const isDateOrderValid = useMemo(() => {
    if (!manufacturingDate || !expiryDate) return true;
    return new Date(expiryDate) > new Date(manufacturingDate);
  }, [manufacturingDate, expiryDate]);

  const uom = selectedMedicine?.unitOfMeasure ?? "units";
  const numQuantity = Number(quantity);

  const resetForm = () => {
    setSupplierId("");
    setMedicineId("");
    setWarehouseId("");
    setStorageLocationId("");
    setBatchNumber("");
    setQuantity("");
    setManufacturingDate("");
    setExpiryDate("");
    setReceipt(null);
    setError(null);
  };

  function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const d = new FormData(e.currentTarget);
    mutation.mutate({
      key: crypto.randomUUID(),
      body: {
        supplierId: d.get("supplierId"),
        warehouseId: d.get("warehouseId"),
        storageLocationId: d.get("storageLocationId"),
        medicineId: d.get("medicineId"),
        batchNumber: d.get("batchNumber"),
        manufacturingDate: d.get("manufacturingDate"),
        expiryDate: d.get("expiryDate"),
        quantity: Number(d.get("quantity"))
      }
    });
  }

  if (medicines.isLoading || suppliers.isLoading || warehouses.isLoading) return <Loading />;
  if (medicines.isError || suppliers.isError || warehouses.isError) {
    return <ErrorBanner message={errorMessage(medicines.error ?? suppliers.error ?? warehouses.error)} />;
  }

  return (
    <section className="space-y-6">
      {/* Page Header */}
      <div className="border-b border-border pb-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-ink">Inbound stock receiving</h1>
            <p className="mt-1 text-xs leading-relaxed text-muted">
              Record physical shipment arrival from authorized pharmaceutical suppliers. This transaction atomically
              registers the batch, sets up the storage bin balance, writes double-entry ledger lines, and logs an immutable audit trail.
            </p>
          </div>
          <span className="inline-flex items-center gap-1.5 rounded-full border border-border bg-surface-soft px-3 py-1 text-xs font-medium text-muted">
            <ShieldCheck size={14} className="text-success" />
            <span>AC-01 FEFO & Shelf-Life Protected</span>
          </span>
        </div>
      </div>

      {/* Success Banner */}
      {receipt && (
        <div
          role="status"
          className="rounded-xl border border-success/30 bg-success-bg/80 p-5 text-ink shadow-xs"
        >
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div className="flex items-start gap-3">
              <div className="rounded-lg bg-success p-2 text-white">
                <CheckCircle2 size={20} />
              </div>
              <div>
                <h2 className="text-base font-bold text-success">Receipt posted successfully</h2>
                <p className="mt-1 text-xs text-ink">
                  Stock batch <strong className="font-mono font-semibold">{batchNumber}</strong> has been received and committed to the ledger.
                </p>
                <div className="mt-3 flex flex-wrap gap-4 font-mono text-xs">
                  <span className="rounded bg-canvas px-2 py-1 border border-border">
                    Journal entry: <strong>{receipt.journalEntryNumber}</strong>
                  </span>
                  <span className="rounded bg-canvas px-2 py-1 border border-border">
                    Available balance: <strong>{receipt.availableQuantity} {uom}</strong>
                  </span>
                </div>
              </div>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <button
                onClick={resetForm}
                className="rounded-lg border border-border bg-canvas px-3 py-1.5 text-xs font-semibold text-ink shadow-xs hover:bg-surface-soft"
              >
                Receive another batch
              </button>
              <Link
                to="/app/inventory"
                className="inline-flex items-center gap-1.5 rounded-lg bg-ink px-3 py-1.5 text-xs font-semibold text-white hover:bg-ink/90"
              >
                <span>View inventory balance</span>
                <ArrowRight size={12} />
              </Link>
            </div>
          </div>
        </div>
      )}

      {/* Main 2-Column Responsive Layout */}
      <div className="grid gap-6 lg:grid-cols-[1.35fr_1fr]">
        {/* Left Column: Form */}
        <div className="rounded-xl border border-border bg-canvas p-6 shadow-xs">
          <div className="mb-5 border-b border-border pb-3">
            <h2 className="text-base font-semibold text-ink">Receipt parameters</h2>
            <p className="text-xs text-muted">All marked fields (<span className="text-danger">*</span>) are mandatory.</p>
          </div>

          <form onSubmit={submit} className="space-y-4">
            {/* Row 1: Supplier & Medicine */}
            <div className="grid gap-4 sm:grid-cols-2">
              <label className="grid gap-1.5 text-xs font-semibold text-ink">
                <span>
                  Supplier <span className="text-danger">*</span>
                </span>
                <select
                  name="supplierId"
                  required
                  value={supplierId}
                  onChange={e => setSupplierId(e.target.value)}
                  className="input text-xs"
                >
                  <option value="">Select authorized supplier…</option>
                  {suppliers.data?.content.map(s => (
                    <option key={s.id} value={s.id}>
                      {s.code} — {s.name}
                    </option>
                  ))}
                </select>
                <span className="text-[11px] font-normal text-muted">Manufacturer or vendor source.</span>
              </label>

              <label className="grid gap-1.5 text-xs font-semibold text-ink">
                <span>
                  Medicine SKU <span className="text-danger">*</span>
                </span>
                <select
                  name="medicineId"
                  required
                  value={medicineId}
                  onChange={e => setMedicineId(e.target.value)}
                  className="input text-xs"
                >
                  <option value="">Select medicine catalog item…</option>
                  {medicines.data?.content
                    .filter(x => x.status === "ACTIVE")
                    .map(x => (
                      <option key={x.id} value={x.id}>
                        {x.sku} — {x.genericName}
                      </option>
                    ))}
                </select>
                <span className="text-[11px] font-normal text-muted">
                  {selectedMedicine ? `Form: ${selectedMedicine.dosageForm} (${selectedMedicine.strength ?? "Standard"})` : "Active catalog formulation."}
                </span>
              </label>
            </div>

            {/* Row 2: Warehouse & Storage Location */}
            <div className="grid gap-4 sm:grid-cols-2">
              <label className="grid gap-1.5 text-xs font-semibold text-ink">
                <span>
                  Receiving warehouse <span className="text-danger">*</span>
                </span>
                <select
                  name="warehouseId"
                  required
                  value={warehouseId}
                  onChange={e => {
                    setWarehouseId(e.target.value);
                    setStorageLocationId(""); // Reset dependent storage location automatically
                  }}
                  className="input text-xs"
                >
                  <option value="">Select destination warehouse…</option>
                  {warehouses.data?.content
                    .filter(x => x.status === "ACTIVE")
                    .map(x => (
                      <option key={x.id} value={x.id}>
                        {x.code} — {x.name}
                      </option>
                    ))}
                </select>
                <span className="text-[11px] font-normal text-muted">Physical repository where stock is unloaded.</span>
              </label>

              <label className="grid gap-1.5 text-xs font-semibold text-ink">
                <span>
                  Storage bin location <span className="text-danger">*</span>
                </span>
                <select
                  name="storageLocationId"
                  required
                  value={storageLocationId}
                  onChange={e => setStorageLocationId(e.target.value)}
                  disabled={!warehouseId || storage.isLoading}
                  className="input text-xs disabled:bg-surface-soft disabled:text-muted"
                >
                  <option value="">
                    {!warehouseId
                      ? "Select warehouse first to view available bins…"
                      : storage.isLoading
                      ? "Loading warehouse storage bins…"
                      : "Select bin location…"}
                  </option>
                  {storage.data?.map(x => (
                    <option key={x.id} value={x.id}>
                      {x.binCode} (Zone: {x.zone ?? "Main"}, Rack: {x.rack ?? "1"})
                    </option>
                  ))}
                </select>
                <span className="text-[11px] font-normal text-muted">
                  {!warehouseId
                    ? "Select receiving warehouse above to populate storage bins."
                    : storage.isLoading
                    ? "Fetching location records…"
                    : "Assigned physical storage rack/shelf/bin."}
                </span>
              </label>
            </div>

            {/* Row 3: Batch Number & Quantity with UoM */}
            <div className="grid gap-4 sm:grid-cols-2">
              <label className="grid gap-1.5 text-xs font-semibold text-ink">
                <span>
                  Batch / Lot number <span className="text-danger">*</span>
                </span>
                <input
                  name="batchNumber"
                  required
                  value={batchNumber}
                  onChange={e => setBatchNumber(e.target.value)}
                  className="input font-mono text-xs"
                  placeholder="e.g. BATCH-2026-001 or LOT-A129"
                />
                <span className="text-[11px] font-normal text-muted">
                  Manufacturer batch identifier (alphanumeric, max 64 chars).
                </span>
              </label>

              <label className="grid gap-1.5 text-xs font-semibold text-ink">
                <span>
                  Quantity received <span className="text-danger">*</span>
                </span>
                <div className="relative flex items-center">
                  <input
                    name="quantity"
                    required
                    type="number"
                    min="1"
                    step="1"
                    value={quantity}
                    onChange={e => setQuantity(e.target.value)}
                    className="input pr-16 text-xs"
                    placeholder="e.g. 250"
                  />
                  <span className="pointer-events-none absolute right-3 font-mono text-xs font-semibold text-muted">
                    {uom}
                  </span>
                </div>
                <span className="text-[11px] font-normal text-muted">
                  Positive whole integer count of {uom}.
                </span>
              </label>
            </div>

            {/* Row 4: Manufacturing & Expiry Dates */}
            <div className="grid gap-4 sm:grid-cols-2">
              <label className="grid gap-1.5 text-xs font-semibold text-ink">
                <span>
                  Manufacturing date <span className="text-danger">*</span>
                </span>
                <input
                  name="manufacturingDate"
                  required
                  type="date"
                  value={manufacturingDate}
                  onChange={e => setManufacturingDate(e.target.value)}
                  className="input text-xs"
                />
                <span className="text-[11px] font-normal text-muted">Production date from supplier Certificate of Analysis (COA).</span>
              </label>

              <label className="grid gap-1.5 text-xs font-semibold text-ink">
                <span>
                  Expiry date <span className="text-danger">*</span>
                </span>
                <input
                  name="expiryDate"
                  required
                  type="date"
                  value={expiryDate}
                  onChange={e => setExpiryDate(e.target.value)}
                  className={`input text-xs ${
                    shelfLifeDays !== null && shelfLifeDays < 90 ? "border-danger bg-danger-bg/20" : ""
                  }`}
                />
                <span className="text-[11px] font-normal text-muted">Expiration date. Minimum 90 days shelf-life required upon receipt (AC-01).</span>
              </label>
            </div>

            {/* Date validation warning */}
            {!isDateOrderValid && (
              <div role="alert" className="flex items-center gap-2 rounded-lg border border-danger/30 bg-danger-bg p-3 text-xs text-danger">
                <AlertTriangle size={16} className="shrink-0" />
                <span>Validation Error: Expiry date must be later than the manufacturing date.</span>
              </div>
            )}

            {/* Shelf-life warning if below 90 days */}
            {shelfLifeDays !== null && shelfLifeDays < 90 && (
              <div role="alert" className="flex items-center gap-2 rounded-lg border border-danger/30 bg-danger-bg p-3 text-xs text-danger">
                <ShieldAlert size={16} className="shrink-0" />
                <span>
                  AC-01 Shelf-Life Warning: Batch has only {shelfLifeDays} days of remaining shelf life. The receiving
                  policy requires at least 90 days and will be rejected with HTTP 422 upon posting.
                </span>
              </div>
            )}

            {/* Error Display */}
            {error ? (
              <div role="alert" className="mt-4">
                <ErrorBanner message={errorMessage(error)} />
              </div>
            ) : null}

            {/* Submit Bar */}
            <div className="pt-3">
              <button
                type="submit"
                disabled={mutation.isPending || !isDateOrderValid}
                className="inline-flex min-h-11 w-full items-center justify-center gap-2 rounded-lg bg-ink px-6 text-sm font-semibold text-canvas shadow-xs transition hover:opacity-90 disabled:bg-surface-strong disabled:text-muted disabled:border disabled:border-border disabled:opacity-90 disabled:cursor-not-allowed sm:w-auto"
              >
                {mutation.isPending ? (
                  <>
                    <RefreshCw size={16} className="animate-spin" />
                    <span>Posting atomic receipt to ledger…</span>
                  </>
                ) : (
                  <>
                    <Save size={16} />
                    <span>Post inbound receipt</span>
                  </>
                )}
              </button>
            </div>
          </form>
        </div>

        {/* Right Column: Live Transaction Summary & Compliance Checklist */}
        <div className="space-y-4">
          {/* Card 1: Live Transaction Preview */}
          <div className="rounded-xl border border-border bg-canvas p-5 shadow-xs">
            <div className="mb-4 border-b border-border pb-3">
              <h2 className="text-sm font-semibold text-ink">Inbound transaction preview</h2>
              <p className="text-[11px] text-muted">Real-time parameters being committed to the ledger.</p>
            </div>

            <div className="space-y-2.5 text-xs">
              <div className="flex justify-between py-1 border-b border-border/50">
                <span className="text-muted">Supplier:</span>
                <span className="font-semibold text-ink">{selectedSupplier?.name ?? "—"}</span>
              </div>
              <div className="flex justify-between py-1 border-b border-border/50">
                <span className="text-muted">Medicine:</span>
                <span className="font-semibold text-ink">{selectedMedicine?.genericName ?? "—"}</span>
              </div>
              <div className="flex justify-between py-1 border-b border-border/50">
                <span className="text-muted">Batch / Lot:</span>
                <span className="font-mono font-semibold text-ink">{batchNumber || "—"}</span>
              </div>
              <div className="flex justify-between py-1 border-b border-border/50">
                <span className="text-muted">Quantity:</span>
                <span className="font-mono font-bold text-ink">
                  {numQuantity > 0 ? `${numQuantity} ${uom}` : "—"}
                </span>
              </div>
              <div className="flex justify-between py-1 border-b border-border/50">
                <span className="text-muted">Receiving Depot:</span>
                <span className="font-semibold text-ink">{selectedWarehouse?.name ?? "—"}</span>
              </div>
              <div className="flex justify-between py-1 border-b border-border/50">
                <span className="text-muted">Storage Bin:</span>
                <span className="font-mono font-semibold text-ink">{selectedStorage?.binCode ?? "—"}</span>
              </div>
              <div className="flex justify-between py-1">
                <span className="text-muted">Shelf Life:</span>
                <span
                  className={`font-semibold ${
                    shelfLifeDays === null
                      ? "text-muted"
                      : shelfLifeDays >= 90
                      ? "text-success"
                      : "text-danger"
                  }`}
                >
                  {shelfLifeDays !== null ? `${shelfLifeDays} days remaining` : "—"}
                </span>
              </div>
            </div>
          </div>

          {/* Card 2: Atomic Accounting & Compliance Assurance */}
          <div className="rounded-xl border border-border bg-surface-soft/40 p-5 text-xs text-muted shadow-xs">
            <div className="mb-3 flex items-center gap-2 text-ink">
              <ShieldCheck size={16} className="text-success" />
              <h3 className="font-semibold">Atomic Ledger Assurance</h3>
            </div>
            <p className="leading-relaxed">
              Upon posting, MedTrack executes the following within an isolated transaction:
            </p>
            <ul className="mt-2.5 space-y-1.5 pl-4 list-disc text-[11px] leading-normal text-body">
              <li>Creates master Batch entity with manufacture and expiry timestamps.</li>
              <li>Updates Inventory Balance for target warehouse and bin location.</li>
              <li>Writes balanced Double-Entry Journal Lines (DR Inventory, CR Inbound Clearing).</li>
              <li>Generates an immutable audit record with user session telemetry.</li>
            </ul>
          </div>
        </div>
      </div>
    </section>
  );
}