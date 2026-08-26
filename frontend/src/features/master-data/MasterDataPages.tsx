import { FormEvent, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, Save, X } from "lucide-react";
import { masterDataApi } from "../../services/masterDataApi";
import { ErrorBanner, Empty, Loading } from "../../components/feedback/States";
import { StatusBadge } from "../../components/ui/StatusBadge";
import { errorMessage } from "../../utils/errors";
import type { Warehouse } from "../../types/api";

function PageTitle({ title, action }: { title: string; action?: React.ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
      <div>
        <h1 className="text-2xl font-semibold text-ink">{title}</h1>
        <p className="text-muted">Authoritative master data used by inventory and fulfillment.</p>
      </div>
      {action}
    </div>
  );
}

function Button({ children, ...props }: React.ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button
      {...props}
      className={`inline-flex min-h-10 items-center justify-center gap-2 rounded bg-ink px-4 text-sm font-medium text-white transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50 ${
        props.className ?? ""
      }`}
    >
      {children}
    </button>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="grid gap-1 text-sm font-medium text-ink">
      <span>{label}</span>
      {children}
    </label>
  );
}

function Panel({ children }: { children: React.ReactNode }) {
  return <div className="rounded-lg border border-border bg-canvas p-4 shadow-sm">{children}</div>;
}

function FormError({ error }: { error: unknown }) {
  return error ? <ErrorBanner message={errorMessage(error)} /> : null;
}

export function MedicinesPage() {
  const [search, setSearch] = useState("");
  const [show, setShow] = useState(false);
  const q = useQuery({
    queryKey: ["medicines", search],
    queryFn: () => masterDataApi.medicines(0, search)
  });

  return (
    <section>
      <PageTitle
        title="Medicines"
        action={
          <Button onClick={() => setShow(v => !v)}>
            <Plus size={16} />
            {show ? "Close form" : "New medicine"}
          </Button>
        }
      />
      {show && <MedicineForm onComplete={() => { setShow(false); q.refetch(); }} />}
      <div className="mb-3">
        <label className="sr-only" htmlFor="medicine-search">Search medicines</label>
        <input
          id="medicine-search"
          value={search}
          onChange={e => setSearch(e.target.value)}
          className="input max-w-md"
          placeholder="Search by SKU or name…"
        />
      </div>
      {q.isLoading ? (
        <Loading />
      ) : q.isError ? (
        <ErrorBanner message={errorMessage(q.error)} />
      ) : !q.data?.content.length ? (
        <Empty title="No medicines found" />
      ) : (
        <Panel>
          <div className="overflow-x-auto">
            <table className="w-full text-left">
              <thead className="text-xs uppercase text-muted">
                <tr>
                  <th className="py-2">SKU</th>
                  <th>Medicine</th>
                  <th>Form / strength</th>
                  <th>Category</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {q.data.content.map(m => (
                  <tr key={m.id} className="hover:bg-surface-soft/40">
                    <td className="py-3 font-mono text-xs font-medium text-ink">{m.sku}</td>
                    <td>
                      <strong className="text-ink">{m.genericName}</strong>
                      {m.brandName && <span className="block text-xs text-muted">{m.brandName}</span>}
                    </td>
                    <td className="text-sm">{m.dosageForm} · {m.strength}</td>
                    <td className="font-mono text-xs text-muted">{m.categoryCode}</td>
                    <td><StatusBadge status={m.status} /></td>
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
        <h2 className="mb-3 font-semibold text-ink">Register new medicine</h2>
        <form onSubmit={submit} className="grid gap-3 md:grid-cols-3">
          <Field label="SKU">
            <input required pattern="MED-[A-Z]{2,12}-[0-9]{5}" name="sku" placeholder="MED-ANT-00042" className="input" />
          </Field>
          <Field label="Generic name">
            <input required name="genericName" className="input" placeholder="e.g. Amoxicillin" />
          </Field>
          <Field label="Brand name">
            <input name="brandName" className="input" placeholder="e.g. Amoxil" />
          </Field>
          <Field label="Category">
            <select required name="categoryId" className="input">
              <option value="">Select category</option>
              {categoriesQuery.data?.map(c => (
                <option key={c.id} value={c.id}>{c.code} — {c.name}</option>
              ))}
            </select>
          </Field>
          <Field label="Dosage form">
            <select required name="dosageForm" defaultValue="TABLET" className="input">
              <option value="TABLET">TABLET</option>
              <option value="CAPSULE">CAPSULE</option>
              <option value="INJECTION">INJECTION</option>
              <option value="SYRUP">SYRUP</option>
              <option value="OINTMENT">OINTMENT</option>
              <option value="IV_FLUID">IV_FLUID</option>
            </select>
          </Field>
          <Field label="Strength">
            <input required name="strength" className="input" placeholder="e.g. 500mg" />
          </Field>
          <Field label="Unit of measure">
            <input required name="unitOfMeasure" defaultValue="BOX" className="input" />
          </Field>
          <Field label="Storage temperature">
            <input name="storageTemp" className="input" placeholder="ROOM_TEMP" defaultValue="ROOM_TEMP" />
          </Field>
          <Field label="Minimum stock threshold">
            <input name="minStockThreshold" type="number" min="0" defaultValue="10" className="input" />
          </Field>
          <Field label="Min receiving shelf life (days)">
            <input name="minReceivingShelfLifeDays" type="number" min="0" defaultValue="90" className="input" />
          </Field>
          <Field label="Status">
            <select name="status" defaultValue="ACTIVE" className="input">
              <option value="ACTIVE">ACTIVE</option>
              <option value="DISCONTINUED">DISCONTINUED</option>
            </select>
          </Field>
          <Field label="Description">
            <input name="description" className="input" placeholder="Optional notes" />
          </Field>
          <div className="md:col-span-3">
            <FormError error={error} />
            <Button type="submit" disabled={mutation.isPending}>
              <Save size={16} />
              {mutation.isPending ? "Saving…" : "Create medicine"}
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