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
    return localStorage.getItem("medtrack-theme") === "dark" ||
      Boolean(!localStorage.getItem("medtrack-theme") && typeof window !== "undefined" && window.matchMedia?.("(prefers-color-scheme: dark)")?.matches);
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
        <div className="mb-6 px-2 text-lg font-semibold text-ink">MedTrack</div>
        <nav aria-label="Primary navigation">
          {nav.map(([to, label, Icon]) => (
            <NavLink
              onClick={() => sidebarOpen && toggleSidebar()}
              key={to}
              to={`/app/${to}`}
              className={({ isActive }) =>
                `mb-1 flex min-h-10 items-center gap-3 rounded px-3 transition-colors ${
                  isActive ? "bg-surface text-ink font-medium" : "text-muted hover:bg-surface-soft hover:text-ink"
                }`
              }
            >
              <Icon size={18} />
              <span>{label}</span>
            </NavLink>
          ))}
        </nav>
      </aside>
      {sidebarOpen && (
        <button
          aria-label="Close navigation"
          className="fixed inset-0 z-20 bg-black/30 lg:hidden"
          onClick={toggleSidebar}
        />
      )}
      <main className="min-w-0">
        <header className="flex h-14 items-center justify-between border-b border-border bg-canvas px-4">
          <button aria-label="Toggle navigation" onClick={toggleSidebar} className="text-ink">
            <Menu size={20} />
          </button>
          <div className="hidden text-sm text-muted md:block">
            Warehouse: <span className="font-medium text-ink">{user?.assignedWarehouseId ?? "All permitted"}</span>
          </div>
          <div className="flex items-center gap-3">
            <CommandPalette />
            <button
              aria-label={dark ? "Switch to light mode" : "Switch to dark mode"}
              onClick={() => setDark(d => !d)}
              className="rounded p-1.5 text-muted hover:bg-surface-soft hover:text-ink"
            >
              {dark ? <Sun size={18} /> : <Moon size={18} />}
            </button>
            <NavLink aria-label="Notifications" to="/app/notifications" className="text-muted hover:text-ink">
              <Bell size={18} />
            </NavLink>
            <button onClick={() => logout()} className="text-sm font-medium text-muted hover:text-ink">
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