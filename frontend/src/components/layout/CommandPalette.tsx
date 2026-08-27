import {
  AlertCircle,
  ArrowRight,
  Boxes,
  ClipboardList,
  Command,
  Download,
  FileSpreadsheet,
  Layers,
  LogOut,
  MapPin,
  Moon,
  PackagePlus,
  RefreshCw,
  ScanLine,
  ScrollText,
  Search,
  Send,
  Sun,
  Tags,
  Truck,
  Users,
  Warehouse,
  X
} from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useUiStore } from "../../store/uiStore";
import { reportApi } from "../../services/reportApi";
import { operationsApi } from "../../services/operationsApi";

type PaletteItem = {
  id: string;
  category: "Operations" | "Master Data" | "Reports & Compliance" | "Quick Actions";
  name: string;
  description: string;
  icon: React.ComponentType<{ size?: number; className?: string }>;
  shortcut?: string;
  onSelect: () => void;
};

export function CommandPalette() {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [selectedIndex, setSelectedIndex] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const listboxRef = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();
  const logout = useUiStore(s => s.logout);

  const allItems: PaletteItem[] = useMemo(
    () => [
      // Operations
      {
        id: "nav-dashboard",
        category: "Operations",
        name: "Dashboard",
        description: "Operations metrics, warehouse scope, and critical alerts",
        icon: Layers,
        onSelect: () => navigate("/app/dashboard")
      },
      {
        id: "nav-inventory",
        category: "Operations",
        name: "Inventory Balances",
        description: "Live warehouse stock, batches, and bin locations",
        icon: Boxes,
        onSelect: () => navigate("/app/inventory")
      },
      {
        id: "nav-inbound",
        category: "Operations",
        name: "Inbound Receiving",
        description: "Receive supplier stock with FEFO and 90-day shelf life checks",
        icon: PackagePlus,
        onSelect: () => navigate("/app/inventory/inbound")
      },
      {
        id: "nav-transfers",
        category: "Operations",
        name: "Stock Transfers",
        description: "Request transfers, run FEFO allocation, pick, and pack",
        icon: Send,
        onSelect: () => navigate("/app/transfers")
      },
      {
        id: "nav-shipments",
        category: "Operations",
        name: "Shipments & Dispatch",
        description: "Create manifests, dispatch freight, and receive at destination",
        icon: Truck,
        onSelect: () => navigate("/app/shipments")
      },
      {
        id: "nav-tracking",
        category: "Operations",
        name: "Shipment Tracking",
        description: "Live GPS milestones and in-transit delivery tracking",
        icon: MapPin,
        onSelect: () => navigate("/app/tracking")
      },
      {
        id: "nav-scanner",
        category: "Operations",
        name: "Barcode & QR Scanner",
        description: "Scan 1D/2D pharmaceutical codes with camera or hardware wedge",
        icon: ScanLine,
        onSelect: () => navigate("/app/scanner")
      },

      // Master Data
      {
        id: "nav-medicines",
        category: "Master Data",
        name: "Medicines Catalog",
        description: "Pharmaceutical SKUs, dosages, categories, and thresholds",
        icon: ClipboardList,
        onSelect: () => navigate("/app/medicines")
      },
      {
        id: "nav-batches",
        category: "Master Data",
        name: "Batch Master",
        description: "Manufacture & expiry dates, lot numbers, and status",
        icon: Tags,
        onSelect: () => navigate("/app/batches")
      },
      {
        id: "nav-suppliers",
        category: "Master Data",
        name: "Suppliers Directory",
        description: "Authorized pharmaceutical manufacturers and distributors",
        icon: Users,
        onSelect: () => navigate("/app/suppliers")
      },
      {
        id: "nav-warehouses",
        category: "Master Data",
        name: "Warehouses & Storage Bins",
        description: "Central depots, retail clinics, racks, shelves, and bins",
        icon: Warehouse,
        onSelect: () => navigate("/app/warehouses")
      },

      // Reports & Compliance
      {
        id: "nav-reports",
        category: "Reports & Compliance",
        name: "Operational Reports",
        description: "Near-expiry risk analysis table and CSV exports",
        icon: FileSpreadsheet,
        onSelect: () => navigate("/app/reports")
      },
      {
        id: "nav-labels",
        category: "Reports & Compliance",
        name: "Batch Labels & Barcodes",
        description: "Generate authenticated 2D QR and 1D Code 128 barcodes",
        icon: Tags,
        onSelect: () => navigate("/app/labels")
      },
      {
        id: "nav-audit",
        category: "Reports & Compliance",
        name: "Audit Trail",
        description: "Immutable, chronological double-entry compliance log",
        icon: ScrollText,
        onSelect: () => navigate("/app/audit")
      },
      {
        id: "nav-notifications",
        category: "Reports & Compliance",
        name: "Notification Center",
        description: "Active server-generated alerts and expiry warnings",
        icon: AlertCircle,
        onSelect: () => navigate("/app/notifications")
      },

      // Quick Actions
      {
        id: "act-export-inventory",
        category: "Quick Actions",
        name: "Export Inventory CSV",
        description: "Download full balance snapshot as CSV spreadsheet",
        icon: Download,
        onSelect: async () => {
          try {
            const blob = await reportApi.inventory();
            const url = URL.createObjectURL(blob);
            const a = document.createElement("a");
            a.href = url;
            a.download = `inventory_snapshot_${new Date().toISOString().slice(0, 10)}.csv`;
            a.click();
            URL.revokeObjectURL(url);
          } catch {
            navigate("/app/reports");
          }
        }
      },
      {
        id: "act-run-alerts",
        category: "Quick Actions",
        name: "Run Expiry & Stock Alert Scan",
        description: "Trigger immediate server-side policy evaluation",
        icon: RefreshCw,
        onSelect: async () => {
          try {
            await operationsApi.evaluateNotifications();
            navigate("/app/notifications");
          } catch {
            navigate("/app/notifications");
          }
        }
      },
      {
        id: "act-toggle-theme",
        category: "Quick Actions",
        name: "Toggle Dark / Light Mode",
        description: "Switch between high-contrast light and dark themes",
        icon: document.documentElement.classList.contains("dark") ? Sun : Moon,
        onSelect: () => {
          const isDark = document.documentElement.classList.contains("dark");
          if (isDark) {
            document.documentElement.setAttribute("data-theme", "light");
            document.documentElement.classList.remove("dark");
            localStorage.setItem("medtrack-theme", "light");
          } else {
            document.documentElement.setAttribute("data-theme", "dark");
            document.documentElement.classList.add("dark");
            localStorage.setItem("medtrack-theme", "dark");
          }
        }
      },
      {
        id: "act-signout",
        category: "Quick Actions",
        name: "Sign Out",
        description: "Terminate current authenticated session securely",
        icon: LogOut,
        onSelect: () => logout()
      }
    ],
    [navigate, logout]
  );

  const filteredItems = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return allItems;
    return allItems.filter(
      item =>
        item.name.toLowerCase().includes(q) ||
        item.description.toLowerCase().includes(q) ||
        item.category.toLowerCase().includes(q)
    );
  }, [allItems, query]);

  useEffect(() => {
    setSelectedIndex(0);
  }, [filteredItems]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setOpen(v => !v);
      }
      if (e.key === "Escape" && open) {
        e.preventDefault();
        setOpen(false);
      }
      if (open) {
        if (e.key === "ArrowDown") {
          e.preventDefault();
          setSelectedIndex(i => (i + 1) % (filteredItems.length || 1));
        } else if (e.key === "ArrowUp") {
          e.preventDefault();
          setSelectedIndex(i => (i - 1 + filteredItems.length) % (filteredItems.length || 1));
        } else if (e.key === "Enter" && filteredItems[selectedIndex]) {
          e.preventDefault();
          filteredItems[selectedIndex].onSelect();
          setOpen(false);
          setQuery("");
        }
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, filteredItems, selectedIndex]);

  useEffect(() => {
    if (open) {
      setTimeout(() => inputRef.current?.focus(), 50);
    }
  }, [open]);

  useEffect(() => {
    if (open && listboxRef.current) {
      const selectedEl = listboxRef.current.querySelector<HTMLElement>('[aria-selected="true"]');
      if (selectedEl && typeof selectedEl.scrollIntoView === "function") {
        selectedEl.scrollIntoView({ block: "nearest" });
      }
    }
  }, [selectedIndex, open]);

  // Group items by category for hierarchical presentation
  const groupedCategories = useMemo(() => {
    const map = new Map<string, PaletteItem[]>();
    for (const item of filteredItems) {
      if (!map.has(item.category)) map.set(item.category, []);
      map.get(item.category)!.push(item);
    }
    return Array.from(map.entries());
  }, [filteredItems]);

  if (!open) {
    return (
      <button
        onClick={() => setOpen(true)}
        className="hidden items-center gap-2 rounded-md border border-border bg-surface-soft px-2.5 py-1.5 text-xs text-muted transition hover:border-border-strong hover:text-ink md:inline-flex"
        aria-label="Open command palette"
        title="Open command palette (Ctrl+K / ⌘K)"
      >
        <Search size={13} className="text-muted" />
        <span>Search actions & pages…</span>
        <kbd className="ml-1 rounded border border-border bg-canvas px-1.5 py-0.5 font-mono text-[10px] text-muted">
          ⌘K
        </kbd>
      </button>
    );
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="command-palette-title"
      aria-label="Command palette"
      className="fixed inset-0 z-50 flex items-start justify-center bg-black/60 p-4 pt-[12vh] backdrop-blur-sm transition-opacity"
      onMouseDown={() => setOpen(false)}
    >
      <div
        className="w-full max-w-xl overflow-hidden rounded-xl border border-border bg-canvas shadow-2xl transition-all"
        onMouseDown={e => e.stopPropagation()}
      >
        <h2 id="command-palette-title" className="sr-only">
          Command Palette and Quick Navigation
        </h2>

        {/* Search header */}
        <div className="flex items-center gap-3 border-b border-border px-4 py-3">
          <Search size={18} className="shrink-0 text-muted" />
          <input
            ref={inputRef}
            className="w-full bg-transparent text-sm font-medium text-ink placeholder:text-muted focus:outline-none"
            value={query}
            onChange={e => setQuery(e.target.value)}
            placeholder="Search pages, actions, inventory, reports…"
            aria-label="Search commands"
          />
          {query && (
            <button
              onClick={() => setQuery("")}
              className="rounded p-1 text-muted hover:text-ink"
              aria-label="Clear search"
            >
              <X size={14} />
            </button>
          )}
          <button
            aria-label="Close command palette (Escape)"
            onClick={() => setOpen(false)}
            className="rounded border border-border bg-surface-soft px-1.5 py-0.5 font-mono text-[11px] text-muted hover:text-ink"
          >
            ESC
          </button>
        </div>

        {/* Results area */}
        <div ref={listboxRef} className="max-h-[50vh] overflow-y-auto p-2" role="listbox">
          {groupedCategories.length === 0 ? (
            <div className="py-8 text-center">
              <Search size={28} className="mx-auto text-muted/50" />
              <p className="mt-2 text-sm font-medium text-ink">No matching commands or actions</p>
              <p className="mt-1 text-xs text-muted">
                Try searching for &ldquo;transfers&rdquo;, &ldquo;receiving&rdquo;, &ldquo;medicines&rdquo;, or &ldquo;reports&rdquo;.
              </p>
            </div>
          ) : (
            groupedCategories.map(([category, items]) => (
              <div key={category} className="mb-2 last:mb-0">
                <div className="px-3 py-1.5 text-[11px] font-semibold uppercase tracking-wider text-muted">
                  {category}
                </div>
                <div className="space-y-0.5">
                  {items.map(item => {
                    const currentIndex = filteredItems.findIndex(x => x.id === item.id);
                    const isSelected = currentIndex === selectedIndex;
                    const Icon = item.icon;

                    return (
                      <button
                        key={item.id}
                        role="option"
                        aria-selected={isSelected}
                        className={`flex w-full items-center justify-between rounded-lg px-3 py-2 text-left transition-colors ${
                          isSelected
                            ? "bg-surface-soft text-ink font-medium shadow-sm"
                            : "text-muted hover:bg-surface-soft/60 hover:text-ink"
                        }`}
                        onClick={() => {
                          item.onSelect();
                          setOpen(false);
                          setQuery("");
                        }}
                      >
                        <div className="flex items-center gap-3 overflow-hidden">
                          <div
                            className={`rounded-md p-1.5 ${
                              isSelected
                                ? "bg-canvas text-ink shadow-xs"
                                : "bg-surface text-muted"
                            }`}
                          >
                            <Icon size={16} />
                          </div>
                          <div className="min-w-0">
                            <div className="truncate text-sm font-medium text-ink">
                              {item.name}
                            </div>
                            <div className="truncate text-xs text-muted">
                              {item.description}
                            </div>
                          </div>
                        </div>
                        {isSelected && (
                          <div className="ml-2 shrink-0 text-muted">
                            <ArrowRight size={14} />
                          </div>
                        )}
                      </button>
                    );
                  })}
                </div>
              </div>
            ))
          )}
        </div>

        {/* Footer shortcuts helper */}
        <div className="flex flex-wrap items-center justify-between border-t border-border bg-surface-soft/60 px-4 py-2 text-[11px] text-muted">
          <div className="flex items-center gap-3">
            <span className="inline-flex items-center gap-1">
              <kbd className="rounded border border-border bg-canvas px-1 font-mono text-[10px]">↑</kbd>
              <kbd className="rounded border border-border bg-canvas px-1 font-mono text-[10px]">↓</kbd>
              <span>Navigate</span>
            </span>
            <span className="inline-flex items-center gap-1">
              <kbd className="rounded border border-border bg-canvas px-1 font-mono text-[10px]">↵</kbd>
              <span>Select</span>
            </span>
            <span className="inline-flex items-center gap-1">
              <kbd className="rounded border border-border bg-canvas px-1 font-mono text-[10px]">ESC</kbd>
              <span>Close</span>
            </span>
          </div>
          <div className="hidden font-mono text-[10px] text-muted sm:inline-block">
            MedTrack Navigator
          </div>
        </div>
      </div>
    </div>
  );
}