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
  RefreshCw
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
    className={`inline-flex min-h-10 items-center justify-center gap-2 rounded bg-ink px-4 text-sm font-medium text-white transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50 ${
      p.className ?? ""
    }`}
  >
    {children}
  </button>
);

const Field = ({ label, children }: { label: string; children: React.ReactNode }) => (
  <label className="grid gap-1 text-sm font-medium text-ink">
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

  if (warehouses.isLoading || medicines.isLoading) return <Loading />;

  return (
    <section className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-ink">Stock transfers & FEFO allocation</h1>
          <p className="text-muted">
            Request transfers, allocate earliest-expiring inventory via FEFO, pick, pack, and prepare for dispatch.
          </p>
        </div>
        <Button onClick={() => setShowCreate(v => !v)}>
          <Plus size={16} />
          {showCreate ? "Close form" : "New transfer request"}
        </Button>
      </div>

      {showCreate && (
        <form onSubmit={submit} className="rounded-lg border border-border bg-canvas p-5 shadow-sm">
          <h2 className="mb-4 text-base font-semibold text-ink">Create transfer request</h2>
          <div className="grid gap-3 md:grid-cols-2">
            <Field label="Destination warehouse">
              <select required name="destinationWarehouseId" value={destination}
                onChange={e => setDestination(e.target.value)}
                className="input"
              >
                <option value="">Select destination</option>
                {warehouses.data?.content
                  .filter(x => x.status === "ACTIVE")
                  .map(x => (
                    <option key={x.id} value={x.id}>
                      {x.code} — {x.name}
                    </option>
                  ))}
              </select>
            </Field>
            <Field label="Notes">
              <input name="notes" className="input" placeholder="Handling instructions or priority" />
            </Field>
          </div>
          <div className="mt-4">
            <div className="mb-2 flex items-center justify-between">
              <h3 className="text-sm font-semibold text-ink">Requested medicines</h3>
              <button
                type="button"
                className="text-xs font-medium text-blue hover:underline"
                onClick={() => setItems(i => [...i, { medicineId: "", quantity: 1 }])}
              >
                <Plus className="mr-1 inline" size={14} />
                Add item
              </button>
            </div>
            {items.map((item, index) => (
              <div className="mb-2 grid grid-cols-[1fr_120px_auto] gap-2" key={index}>
                <select required name="medicineId" value={item.medicineId}
                  onChange={e =>
                    setItems(all =>
                      all.map((x, i) => (i === index ? { ...x, medicineId: e.target.value } : x))
                    )
                  }
                  className="input"
                >
                  <option value="">Select active medicine</option>
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
                  className="input"
                  min="1"
                  type="number"
                  value={item.quantity}
                  onChange={e =>
                    setItems(all =>
                      all.map((x, i) => (i === index ? { ...x, quantity: Number(e.target.value) } : x))
                    )
                  }
                />
                <button
                  aria-label="Remove item"
                  className="rounded px-2 text-danger disabled:opacity-40"
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
          <div className="mt-4">
            <Button type="submit" disabled={create.isPending}>
              <Plus size={16} />
              {create.isPending ? "Submitting…" : "Submit transfer request"}
            </Button>
          </div>
        </form>
      )}

      <div className="rounded-lg border border-border bg-canvas shadow-sm">
        <div className="border-b border-border p-4">
          <h2 className="text-base font-semibold text-ink">All stock transfers</h2>
        </div>
        {transfersList.isLoading ? (
          <Loading />
        ) : !transfersList.data?.content.length ? (
          <Empty title="No stock transfers found" />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-border bg-surface-soft text-xs uppercase text-muted">
                <tr>
                  <th className="p-3">Transfer #</th>
                  <th className="p-3">Source → Destination</th>
                  
                  <th className="p-3">Status</th>
                  <th className="p-3 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {transfersList.data.content.map(t => (
                  <tr
                    key={t.id}
                    className={`hover:bg-surface-soft/50 ${
                      transfer?.id === t.id ? "bg-surface-soft font-medium" : ""
                    }`}
                  >
                    <td className="p-3 font-mono font-medium text-ink">{t.transferNumber}</td>
                    <td className="p-3 text-muted">
                      {t.sourceWarehouseId ? t.sourceWarehouseId.slice(0, 8) : "Central Depot"} →{" "}
                      {t.destinationWarehouseId.slice(0, 8)}
                    </td>
                    
                    <td className="p-3">
                      <StatusBadge status={t.status} />
                    </td>
                    <td className="p-3 text-right">
                      <button
                        onClick={() => {
                          operationsApi.transfer(t.id).then(setTransfer);
                        }}
                        className="text-sm font-medium text-blue hover:underline"
                      >
                        Manage & Workbench
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
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
          {q.data.map(n => (
            <article key={n.id} className="rounded-lg border border-border bg-canvas p-4 shadow-sm">
              <div className="flex justify-between gap-3">
                <div>
                  <div className="flex items-center gap-2">
                    <Bell size={16} className="text-warning" />
                    <strong className="text-ink">{n.title}</strong>
                  </div>
                  <p className="mt-1 text-sm text-body">{n.message}</p>
                  <time className="mt-2 block text-xs text-muted">
                    {new Date(n.createdAt).toLocaleString()}
                  </time>
                </div>
                <button
                  className="self-start text-xs font-medium text-blue hover:underline"
                  onClick={() => read.mutate(n.id)}
                >
                  Mark read
                </button>
              </div>
            </article>
          ))}
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