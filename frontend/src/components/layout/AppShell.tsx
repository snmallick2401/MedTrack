import {
  Bell,
  Boxes,
  ClipboardList,
  LayoutDashboard,
  Map,
  Menu,
  Moon,
  Pill,
  FileSpreadsheet,
  Sun,
  Truck,
  Warehouse,
  PackagePlus,
  BoxesIcon,
  Tags,
  ScrollText,
  ScanLine
} from "lucide-react";
import { useEffect, useState } from "react";
import { NavLink, Outlet } from "react-router-dom";
import { useUiStore } from "../../store/uiStore";
import { CommandPalette } from "./CommandPalette";

const nav = [
  ["dashboard", "Dashboard", LayoutDashboard],
  ["inventory", "Inventory", Boxes],
  ["inventory/inbound", "Inbound receiving", PackagePlus],
  ["medicines", "Medicines", Pill],
  ["batches", "Batches", BoxesIcon],
  ["suppliers", "Suppliers", Tags],
  ["warehouses", "Warehouses", Warehouse],
  ["transfers", "Transfers", ClipboardList],
  ["shipments", "Shipments", Truck],
  ["tracking", "Tracking", Map],
  ["labels", "Batch labels", Tags],
  ["scanner", "Scanner", ScanLine],
  ["reports", "Reports", FileSpreadsheet],
  ["audit", "Audit logs", ScrollText]
] as const;

export function AppShell() {
  const { user, sidebarOpen, toggleSidebar, logout } = useUiStore();
  const [dark, setDark] = useState(() => {
    return (
      localStorage.getItem("medtrack-theme") === "dark" ||
      Boolean(
        !localStorage.getItem("medtrack-theme") &&
          typeof window !== "undefined" &&
          window.matchMedia?.("(prefers-color-scheme: dark)")?.matches
      )
    );
  });

  useEffect(() => {
    if (dark) {
      document.documentElement.setAttribute("data-theme", "dark");
      document.documentElement.classList.add("dark");
      localStorage.setItem("medtrack-theme", "dark");
    } else {
      document.documentElement.setAttribute("data-theme", "light");
      document.documentElement.classList.remove("dark");
      localStorage.setItem("medtrack-theme", "light");
    }
  }, [dark]);

  return (
    <div className="min-h-screen lg:grid lg:grid-cols-[240px_1fr]">
      <aside
        className={`${
          sidebarOpen ? "fixed inset-y-0 left-0 z-30 block w-60 shadow-xl" : "hidden"
        } border-r border-border bg-canvas p-3 lg:static lg:block lg:w-auto lg:shadow-none`}
      >
        <div className="mb-6 flex items-center justify-between px-2">
          <div className="text-lg font-bold tracking-tight text-ink">MedTrack</div>
          <span className="rounded bg-surface-soft px-1.5 py-0.5 text-[10px] font-mono text-muted">
            v2026.5
          </span>
        </div>
        <nav aria-label="Primary navigation" className="space-y-0.5">
          {nav.map(([to, label, Icon]) => (
            <NavLink
              onClick={() => sidebarOpen && toggleSidebar()}
              key={to}
              to={`/app/${to}`}
              end={to === "inventory"}
              className={({ isActive }) =>
                `flex min-h-10 items-center gap-3 rounded-lg px-3 text-sm transition-colors ${
                  isActive
                    ? "bg-surface-soft text-ink font-semibold shadow-xs"
                    : "text-muted hover:bg-surface-soft/60 hover:text-ink"
                }`
              }
            >
              <Icon size={18} className="shrink-0" />
              <span>{label}</span>
            </NavLink>
          ))}
        </nav>
        <div className="mt-8 rounded-lg border border-border bg-surface-soft/50 p-3 text-xs">
          <div className="font-semibold text-ink">{user?.fullName ?? user?.email}</div>
          <div className="mt-0.5 font-mono text-[11px] text-muted">{user?.role}</div>
        </div>
      </aside>

      {sidebarOpen && (
        <button
          aria-label="Close navigation"
          className="fixed inset-0 z-20 bg-black/40 backdrop-blur-xs lg:hidden"
          onClick={toggleSidebar}
        />
      )}

      <main className="min-w-0">
        <header className="flex h-14 items-center justify-between border-b border-border bg-canvas px-4">
          <div className="flex items-center gap-3">
            <button
              aria-label="Toggle navigation"
              title="Toggle navigation"
              onClick={toggleSidebar}
              className="rounded-lg p-1.5 text-ink hover:bg-surface-soft lg:hidden"
            >
              <Menu size={20} />
            </button>
            <div className="hidden text-xs text-muted md:block">
              Context:{" "}
              <span className="font-semibold text-ink">
                {user?.assignedWarehouseId ? `Depot ${user.assignedWarehouseId}` : "Enterprise (All Warehouses)"}
              </span>
            </div>
          </div>
          <div className="flex items-center gap-3">
            <CommandPalette />
            <button
              aria-label={dark ? "Switch to light mode" : "Switch to dark mode"}
              title={dark ? "Switch to light mode" : "Switch to dark mode"}
              onClick={() => setDark(d => !d)}
              className="rounded-lg p-1.5 text-muted hover:bg-surface-soft hover:text-ink"
            >
              {dark ? <Sun size={18} /> : <Moon size={18} />}
            </button>
            <NavLink
              aria-label="Notifications"
              title="Notifications"
              to="/app/notifications"
              className="rounded-lg p-1.5 text-muted hover:bg-surface-soft hover:text-ink"
            >
              <Bell size={18} />
            </NavLink>
            <button
              onClick={() => logout()}
              className="rounded-lg px-2 py-1 text-xs font-medium text-muted hover:bg-surface-soft hover:text-ink"
            >
              Sign out
            </button>
          </div>
        </header>
        <div className="mx-auto max-w-[1440px] p-4 md:p-8">
          <Outlet />
        </div>
      </main>
    </div>
  );
}