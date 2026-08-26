import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import {
  Boxes,
  ClipboardList,
  Truck,
  AlertTriangle,
  ArrowRight,
  PackagePlus,
  Send,
  MapPin,
  CheckCircle2
} from "lucide-react";
import { useUiStore } from "../../store/uiStore";
import { operationsApi } from "../../services/operationsApi";
import { reportApi } from "../../services/reportApi";
import { Loading } from "../../components/feedback/States";

export function DashboardPage() {
  const user = useUiStore(s => s.user);

  const notificationsQuery = useQuery({
    queryKey: ["dashboardNotifications"],
    queryFn: operationsApi.notifications
  });

  const expiryQuery = useQuery({
    queryKey: ["dashboardExpiry"],
    queryFn: () => reportApi.expiryData(90)
  });

  const notifications = notificationsQuery.data ?? [];
  const nearExpiryBatches = expiryQuery.data ?? [];

  return (
    <section className="space-y-8">
      <div>
        <h1 className="text-2xl font-semibold text-ink">Operations dashboard</h1>
        <p className="text-muted">
          Welcome back, <span className="font-medium text-ink">{user?.fullName ?? "Operator"}</span> ({user?.role}).
        </p>
      </div>

      {/* KPI Cards */}
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Link
          to="/app/notifications"
          className="group rounded-lg border border-border bg-canvas p-5 shadow-sm transition-all hover:border-border-strong hover:shadow-md"
        >
          <div className="flex items-center justify-between">
            <span className="text-sm font-medium text-muted">Unread Alerts</span>
            <div className="rounded-md bg-info-bg p-2 text-info">
              <AlertTriangle size={20} />
            </div>
          </div>
          <div className="mt-4 flex items-baseline gap-2">
            <span className="text-3xl font-bold text-ink">{notifications.length}</span>
            <span className="text-xs text-muted">active policies</span>
          </div>
          <div className="mt-3 flex items-center text-xs font-medium text-blue group-hover:underline">
            View alerts <ArrowRight size={12} className="ml-1" />
          </div>
        </Link>

        <Link
          to="/app/reports"
          className="group rounded-lg border border-border bg-canvas p-5 shadow-sm transition-all hover:border-border-strong hover:shadow-md"
        >
          <div className="flex items-center justify-between">
            <span className="text-sm font-medium text-muted">Near-Expiry Risk (90d)</span>
            <div className="rounded-md bg-warning-bg p-2 text-warning">
              <Boxes size={20} />
            </div>
          </div>
          <div className="mt-4 flex items-baseline gap-2">
            <span className="text-3xl font-bold text-ink">{nearExpiryBatches.length}</span>
            <span className="text-xs text-muted">batches at risk</span>
          </div>
          <div className="mt-3 flex items-center text-xs font-medium text-blue group-hover:underline">
            View expiry report <ArrowRight size={12} className="ml-1" />
          </div>
        </Link>

        <Link
          to="/app/transfers"
          className="group rounded-lg border border-border bg-canvas p-5 shadow-sm transition-all hover:border-border-strong hover:shadow-md"
        >
          <div className="flex items-center justify-between">
            <span className="text-sm font-medium text-muted">Transfers</span>
            <div className="rounded-md bg-success-bg p-2 text-success">
              <ClipboardList size={20} />
            </div>
          </div>
          <div className="mt-4 flex items-baseline gap-2">
            <span className="text-sm font-medium text-ink">FEFO Multi-Depot</span>
          </div>
          <div className="mt-3 flex items-center text-xs font-medium text-blue group-hover:underline">
            Manage transfers <ArrowRight size={12} className="ml-1" />
          </div>
        </Link>

        <Link
          to="/app/shipments"
          className="group rounded-lg border border-border bg-canvas p-5 shadow-sm transition-all hover:border-border-strong hover:shadow-md"
        >
          <div className="flex items-center justify-between">
            <span className="text-sm font-medium text-muted">Shipments & Transit</span>
            <div className="rounded-md bg-surface-soft p-2 text-ink">
              <Truck size={20} />
            </div>
          </div>
          <div className="mt-4 flex items-baseline gap-2">
            <span className="text-sm font-medium text-ink">Milestones & Telemetry</span>
          </div>
          <div className="mt-3 flex items-center text-xs font-medium text-blue group-hover:underline">
            View shipments <ArrowRight size={12} className="ml-1" />
          </div>
        </Link>
      </div>

      {/* Quick Launch Operations */}
      <div className="rounded-lg border border-border bg-canvas p-5 shadow-sm">
        <h2 className="mb-4 text-base font-semibold text-ink">Operational quick actions</h2>
        <div className="grid gap-3 sm:grid-cols-2 md:grid-cols-4">
          <Link
            to="/app/inventory/inbound"
            className="flex items-center gap-3 rounded-lg border border-border p-3 text-sm font-medium text-ink hover:bg-surface-soft"
          >
            <PackagePlus className="text-success" size={20} />
            <div>
              <div>Inbound receipt</div>
              <div className="text-xs text-muted">Receive supplier stock</div>
            </div>
          </Link>
          <Link
            to="/app/transfers"
            className="flex items-center gap-3 rounded-lg border border-border p-3 text-sm font-medium text-ink hover:bg-surface-soft"
          >
            <Send className="text-blue" size={20} />
            <div>
              <div>New transfer</div>
              <div className="text-xs text-muted">Request stock transfer</div>
            </div>
          </Link>
          <Link
            to="/app/tracking"
            className="flex items-center gap-3 rounded-lg border border-border p-3 text-sm font-medium text-ink hover:bg-surface-soft"
          >
            <MapPin className="text-warning" size={20} />
            <div>
              <div>Live tracking</div>
              <div className="text-xs text-muted">Simulate route GPS</div>
            </div>
          </Link>
          <Link
            to="/app/reports"
            className="flex items-center gap-3 rounded-lg border border-border p-3 text-sm font-medium text-ink hover:bg-surface-soft"
          >
            <CheckCircle2 className="text-ink" size={20} />
            <div>
              <div>CSV exports</div>
              <div className="text-xs text-muted">Download audit data</div>
            </div>
          </Link>
        </div>
      </div>
    </section>
  );
}