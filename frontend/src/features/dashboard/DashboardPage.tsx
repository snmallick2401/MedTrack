import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import {
  AlertTriangle,
  ArrowRight,
  Boxes,
  CheckCircle2,
  Clock,
  Download,
  FileSpreadsheet,
  Layers,
  MapPin,
  PackagePlus,
  RefreshCw,
  Send,
  ShieldAlert,
  Truck
} from "lucide-react";
import { useUiStore } from "../../store/uiStore";
import { operationsApi } from "../../services/operationsApi";
import { reportApi } from "../../services/reportApi";

type MetricProps = {
  title: string;
  value?: number;
  unavailable: boolean;
  description: string;
  to: string;
  icon: React.ReactNode;
  tone: "info" | "warning" | "success" | "neutral";
};

function MetricCard({ title, value, unavailable, description, to, icon, tone }: MetricProps) {
  const toneClass = {
    info: "bg-info-bg text-info border-info/20",
    warning: "bg-warning-bg text-warning border-warning/20",
    success: "bg-success-bg text-success border-success/20",
    neutral: "bg-surface-soft text-ink border-border"
  }[tone];

  return (
    <Link
      to={to}
      className="group flex flex-col justify-between rounded-xl border border-border bg-canvas p-4 shadow-xs transition hover:border-border-strong hover:shadow-md"
    >
      <div className="flex items-center justify-between">
        <span className="text-xs font-semibold uppercase tracking-wider text-muted">{title}</span>
        <div className={`rounded-lg border p-2 ${toneClass}`}>{icon}</div>
      </div>
      <div className="my-2">
        <span className="text-2xl font-bold tracking-tight text-ink">
          {unavailable ? "—" : value ?? 0}
        </span>
        <span className="ml-2 text-xs text-muted">
          {unavailable ? "Data unavailable" : description}
        </span>
      </div>
      <div className="flex items-center text-xs font-medium text-blue group-hover:underline">
        <span>View details</span>
        <ArrowRight size={12} className="ml-1" />
      </div>
    </Link>
  );
}

function formatDate(dateStr?: string): string {
  if (!dateStr) return "N/A";
  try {
    const d = new Date(dateStr);
    if (isNaN(d.getTime())) return dateStr;
    return new Intl.DateTimeFormat("en-US", {
      month: "short",
      day: "numeric",
      year: "numeric"
    }).format(d);
  } catch {
    return dateStr;
  }
}

function formatFullTimestamp(timestampMs: number): string {
  if (!timestampMs) return "Awaiting data…";
  try {
    const d = new Date(timestampMs);
    const datePart = new Intl.DateTimeFormat("en-US", {
      month: "short",
      day: "numeric",
      year: "numeric"
    }).format(d);
    const timePart = new Intl.DateTimeFormat("en-US", {
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
      hour12: true,
      timeZoneName: "short"
    }).format(d);
    return `Last updated: ${datePart}, ${timePart}`;
  } catch {
    return `Last updated: ${new Date(timestampMs).toLocaleString()}`;
  }
}

export function DashboardPage() {
  const user = useUiStore(s => s.user);
  const alerts = useQuery({ queryKey: ["dashboardNotifications"], queryFn: operationsApi.notifications });
  const expiry = useQuery({ queryKey: ["dashboardExpiry"], queryFn: () => reportApi.expiryData(90) });
  const transfers = useQuery({ queryKey: ["dashboardTransfers"], queryFn: () => operationsApi.transfers() });
  const shipments = useQuery({ queryKey: ["dashboardShipments"], queryFn: () => operationsApi.shipments() });

  const updatedAt = Math.max(
    alerts.dataUpdatedAt || 0,
    expiry.dataUpdatedAt || 0,
    transfers.dataUpdatedAt || 0,
    shipments.dataUpdatedAt || 0
  );

  const scopeText = user?.assignedWarehouseId
    ? "Warehouse Scoped (Assigned Depot)"
    : "Enterprise Scope (All Warehouses)";

  return (
    <section className="space-y-6">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-border pb-4">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-bold tracking-tight text-ink">Operations dashboard</h1>
            <span className="rounded-md border border-border bg-surface-soft px-2 py-0.5 font-mono text-[11px] font-medium text-muted">
              {user?.role ?? "AUTHENTICATED"}
            </span>
          </div>
          <p className="mt-1 text-xs text-muted">
            Welcome back, <strong className="font-semibold text-ink">{user?.fullName ?? user?.email}</strong> ·{" "}
            <span>{scopeText}</span>
          </p>
        </div>
        <div className="flex items-center gap-3">
          <span className="flex items-center gap-1.5 text-xs text-muted" aria-live="polite">
            <Clock size={13} className="text-muted" />
            <span>{formatFullTimestamp(updatedAt)}</span>
          </span>
        </div>
      </div>

      {/* 4 Primary Operational Metric Cards */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <MetricCard
          title="Active Alerts"
          value={alerts.data?.length}
          unavailable={alerts.isError}
          description="requiring review"
          to="/app/notifications"
          icon={<AlertTriangle size={18} />}
          tone="warning"
        />
        <MetricCard
          title="Near Expiry (≤90d)"
          value={expiry.data?.length}
          unavailable={expiry.isError}
          description="batches at risk"
          to="/app/reports"
          icon={<Boxes size={18} />}
          tone="info"
        />
        <MetricCard
          title="Stock Transfers"
          value={transfers.data?.totalElements}
          unavailable={transfers.isError}
          description="transfers in flight"
          to="/app/transfers"
          icon={<Send size={18} />}
          tone="success"
        />
        <MetricCard
          title="Shipments"
          value={shipments.data?.totalElements}
          unavailable={shipments.isError}
          description="freight dispatches"
          to="/app/shipments"
          icon={<Truck size={18} />}
          tone="neutral"
        />
      </div>

      {/* Main split view: Operational Alerts + Quick Actions */}
      <div className="grid gap-5 lg:grid-cols-[1.4fr_1fr]">
        {/* Alerts & Exceptions Feed */}
        <section className="flex flex-col justify-between rounded-xl border border-border bg-canvas p-5 shadow-xs">
          <div>
            <div className="mb-4 flex flex-wrap items-center justify-between gap-2 border-b border-border pb-3">
              <div>
                <h2 className="text-base font-semibold text-ink">Operational alerts & exceptions</h2>
                <p className="text-xs text-muted">
                  High-priority expiry, stock depletion, and delayed shipment signals.
                </p>
              </div>
              <Link
                to="/app/notifications"
                className="inline-flex items-center text-xs font-semibold text-blue hover:underline"
              >
                View all alerts <ArrowRight size={12} className="ml-1" />
              </Link>
            </div>

            {alerts.isLoading ? (
              <div className="py-8 text-center text-xs text-muted">Loading active alerts…</div>
            ) : alerts.isError ? (
              <div role="alert" className="rounded-lg border border-danger/30 bg-danger-bg p-3 text-xs text-danger">
                Failed to load operational alerts from server.
              </div>
            ) : !alerts.data?.length ? (
              <div className="flex flex-col items-center justify-center rounded-lg border border-dashed border-border p-8 text-center">
                <CheckCircle2 size={24} className="text-success" />
                <p className="mt-2 text-sm font-semibold text-ink">No critical exceptions</p>
                <p className="mt-0.5 text-xs text-muted">
                  All warehouse inventory batches are within nominal shelf life and threshold limits.
                </p>
              </div>
            ) : (
              <div className="space-y-3">
                {alerts.data.slice(0, 3).map(alert => {
                  const isCritical =
                    alert.type.includes("CRITICAL") || alert.type.includes("EXPIRED");
                  const isLow = alert.type.includes("LOW");
                  const isDelay = alert.type.includes("DELAY");

                  const badgeClass = isCritical
                    ? "border-danger/30 bg-danger-bg text-danger"
                    : isDelay || isLow
                    ? "border-warning/30 bg-warning-bg text-warning"
                    : "border-info/30 bg-info-bg text-info";

                  const badgeLabel = isCritical
                    ? "Critical Expiry (≤30 Days)"
                    : isDelay
                    ? "Shipment Delayed"
                    : isLow
                    ? "Low Stock Threshold"
                    : "Near Expiry (≤90 Days)";

                  return (
                    <article
                      key={alert.id}
                      className="rounded-lg border border-border bg-surface-soft/40 p-3.5 transition hover:bg-surface-soft/80"
                    >
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <div className="flex items-center gap-2">
                          {isCritical ? (
                            <ShieldAlert size={16} className="shrink-0 text-danger" />
                          ) : (
                            <AlertTriangle size={16} className="shrink-0 text-warning" />
                          )}
                          <strong className="text-sm font-semibold text-ink">{alert.title}</strong>
                        </div>
                        <span
                          className={`inline-flex shrink-0 items-center whitespace-nowrap rounded-full border px-2 py-0.5 text-[11px] font-semibold ${badgeClass}`}
                        >
                          {badgeLabel}
                        </span>
                      </div>
                      <p className="mt-1.5 text-xs leading-relaxed text-body">{alert.message}</p>
                      <div className="mt-2.5 flex flex-wrap items-center justify-between gap-2 text-[11px] text-muted">
                        <span>Created: {formatDate(alert.createdAt)}</span>
                        <Link
                          to="/app/notifications"
                          className="font-medium text-blue hover:underline"
                        >
                          Review & acknowledge →
                        </Link>
                      </div>
                    </article>
                  );
                })}
              </div>
            )}
          </div>
        </section>

        {/* Operational Quick Actions */}
        <section className="flex flex-col justify-between rounded-xl border border-border bg-canvas p-5 shadow-xs">
          <div>
            <div className="mb-4 border-b border-border pb-3">
              <h2 className="text-base font-semibold text-ink">Operational quick actions</h2>
              <p className="text-xs text-muted">Frequently executed workflow shortcuts.</p>
            </div>
            <div className="grid gap-2.5">
              <QuickActionItem
                to="/app/inventory/inbound"
                icon={<PackagePlus className="text-success" size={18} />}
                title="Inbound Receiving"
                description="Receive supplier stock with FEFO & shelf life validation"
              />
              <QuickActionItem
                to="/app/transfers"
                icon={<Send className="text-blue" size={18} />}
                title="New Transfer Request"
                description="Request depot replenishment & allocate earliest batches"
              />
              <QuickActionItem
                to="/app/shipments"
                icon={<Truck className="text-warning" size={18} />}
                title="Shipment Dispatch"
                description="Create manifests, assign carriers, & receive at destination"
              />
              <QuickActionItem
                to="/app/tracking"
                icon={<MapPin className="text-ink" size={18} />}
                title="Shipment Milestones"
                description="Record GPS tracking events & in-transit checkpoints"
              />
              <QuickActionItem
                to="/app/reports"
                icon={<FileSpreadsheet className="text-ink" size={18} />}
                title="Reports & CSV Exports"
                description="Download batch shelf-life analytics & full inventory ledger"
              />
            </div>
          </div>
        </section>
      </div>
    </section>
  );
}

function QuickActionItem({
  to,
  icon,
  title,
  description
}: {
  to: string;
  icon: React.ReactNode;
  title: string;
  description: string;
}) {
  return (
    <Link
      to={to}
      className="group flex items-center justify-between rounded-lg border border-border p-3 transition hover:border-border-strong hover:bg-surface-soft"
    >
      <div className="flex items-center gap-3 overflow-hidden">
        <div className="rounded-md border border-border bg-canvas p-2 shadow-xs transition group-hover:scale-105">
          {icon}
        </div>
        <div className="min-w-0">
          <div className="truncate text-xs font-semibold text-ink group-hover:text-blue">
            {title}
          </div>
          <div className="truncate text-[11px] text-muted">{description}</div>
        </div>
      </div>
      <ArrowRight size={14} className="shrink-0 text-muted group-hover:text-blue" />
    </Link>
  );
}