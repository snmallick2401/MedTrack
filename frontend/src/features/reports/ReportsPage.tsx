import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  AlertOctagon,
  AlertTriangle,
  ChevronLeft,
  ChevronRight,
  Clock3,
  Download,
  FileSpreadsheet,
  Filter,
  Search,
  ShieldAlert,
  X
} from "lucide-react";
import { reportApi, ExpiryReportItem } from "../../services/reportApi";
import { Loading, ErrorBanner, Empty } from "../../components/feedback/States";
import { StatusBadge } from "../../components/ui/StatusBadge";
import { errorMessage } from "../../utils/errors";

export function ReportsPage() {
  const [days, setDays] = useState(90);
  const [search, setSearch] = useState("");
  const [severityFilter, setSeverityFilter] = useState<"ALL" | "CRITICAL" | "WARNING">("ALL");
  const [downloadingInv, setDownloadingInv] = useState(false);
  const [downloadingExp, setDownloadingExp] = useState(false);

  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);

  const expiryQuery = useQuery({
    queryKey: ["expiryReportData", days],
    queryFn: () => reportApi.expiryData(days)
  });

  const allItems = expiryQuery.data ?? [];

  // Summary Metrics
  const metrics = useMemo(() => {
    const total = allItems.length;
    const critical = allItems.filter(i => i.daysToExpiry <= 30).length;
    const warning = allItems.filter(i => i.daysToExpiry > 30 && i.daysToExpiry <= 90).length;
    const longTerm = allItems.filter(i => i.daysToExpiry > 90).length;
    return { total, critical, warning, longTerm };
  }, [allItems]);

  // Filter items by search & severity
  const filteredItems = useMemo(() => {
    return allItems.filter(item => {
      if (severityFilter === "CRITICAL" && item.daysToExpiry > 30) return false;
      if (severityFilter === "WARNING" && (item.daysToExpiry <= 30 || item.daysToExpiry > 90)) return false;

      if (!search.trim()) return true;
      const term = search.toLowerCase();
      return (
        item.genericName.toLowerCase().includes(term) ||
        item.medicineSku.toLowerCase().includes(term) ||
        item.batchNumber.toLowerCase().includes(term) ||
        item.warehouseName.toLowerCase().includes(term) ||
        item.warehouseCode.toLowerCase().includes(term)
      );
    });
  }, [allItems, severityFilter, search]);

  // Pagination
  const totalItems = filteredItems.length;
  const totalPages = Math.max(1, Math.ceil(totalItems / pageSize));
  const currentPage = Math.min(page, totalPages - 1);
  const paginatedItems = useMemo(() => {
    const start = Math.max(0, currentPage) * pageSize;
    return filteredItems.slice(start, start + pageSize);
  }, [filteredItems, currentPage, pageSize]);

  const pageStart = totalItems === 0 ? 0 : Math.max(0, currentPage) * pageSize + 1;
  const pageEnd = Math.min((Math.max(0, currentPage) + 1) * pageSize, totalItems);

  async function downloadInventoryCsv() {
    try {
      setDownloadingInv(true);
      const blob = await reportApi.inventory();
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = "inventory_balance_report.csv";
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      alert("Failed to download inventory CSV: " + errorMessage(e));
    } finally {
      setDownloadingInv(false);
    }
  }

  async function downloadExpiryCsv() {
    try {
      setDownloadingExp(true);
      const blob = await reportApi.expiryCsv(days);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `near_expiry_report_${days}d.csv`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      alert("Failed to download expiry CSV: " + errorMessage(e));
    } finally {
      setDownloadingExp(false);
    }
  }

  return (
    <section className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-border pb-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-ink">Operational reports & risk exports</h1>
          <p className="mt-1 text-xs text-muted">
            Authoritative CSV exports, near-expiry risk analysis, and batch shelf-life monitoring.
          </p>
        </div>
        <div className="flex flex-wrap gap-2.5">
          <button
            onClick={downloadInventoryCsv}
            disabled={downloadingInv}
            className="inline-flex min-h-10 items-center justify-center gap-2 rounded-lg bg-ink px-4 text-sm font-semibold text-canvas shadow-xs transition hover:opacity-90 active:scale-[0.98] disabled:bg-surface-strong disabled:text-muted disabled:border disabled:border-border disabled:opacity-85 disabled:cursor-not-allowed"
          >
            <Download size={16} />
            <span>{downloadingInv ? "Exporting balance…" : "Export inventory CSV"}</span>
          </button>
          <button
            onClick={downloadExpiryCsv}
            disabled={downloadingExp}
            className="inline-flex min-h-10 items-center justify-center gap-2 rounded-lg border border-border bg-canvas px-4 text-sm font-semibold text-ink shadow-xs transition hover:bg-surface-soft active:scale-[0.98] disabled:opacity-50"
          >
            <FileSpreadsheet size={16} />
            <span>{downloadingExp ? "Exporting expiry…" : `Export expiry (${days}d) CSV`}</span>
          </button>
        </div>
      </div>

      {/* Summary KPI Risk Overview Cards */}
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <div className="rounded-xl border border-border bg-canvas p-3.5 shadow-xs">
          <span className="text-[11px] font-medium text-muted uppercase tracking-wider">Total At-Risk Batches</span>
          <p className="mt-1 text-2xl font-bold text-ink">{metrics.total}</p>
        </div>
        <div className="rounded-xl border border-danger/30 bg-danger-bg/40 p-3.5 shadow-xs">
          <div className="flex items-center gap-1.5 text-danger">
            <AlertOctagon size={14} />
            <span className="text-[11px] font-bold uppercase tracking-wider">Critical (≤ 30 Days)</span>
          </div>
          <p className="mt-1 text-2xl font-bold text-danger">{metrics.critical}</p>
        </div>
        <div className="rounded-xl border border-warning/30 bg-warning-bg/40 p-3.5 shadow-xs">
          <div className="flex items-center gap-1.5 text-warning">
            <Clock3 size={14} />
            <span className="text-[11px] font-bold uppercase tracking-wider">Near Expiry (31–90 Days)</span>
          </div>
          <p className="mt-1 text-2xl font-bold text-warning">{metrics.warning}</p>
        </div>
        <div className="rounded-xl border border-border bg-canvas p-3.5 shadow-xs">
          <span className="text-[11px] font-medium text-muted uppercase tracking-wider">Extended Horizon</span>
          <p className="mt-1 text-2xl font-bold text-ink">{metrics.longTerm}</p>
        </div>
      </div>

      {/* Main Table Panel */}
      <div className="rounded-xl border border-border bg-canvas shadow-xs overflow-hidden">
        {/* Table Header & Threshold Selector Bar */}
        <div className="flex flex-wrap items-center justify-between gap-4 border-b border-border bg-surface-soft/30 p-4">
          <div className="flex items-center gap-2">
            <ShieldAlert className="text-warning shrink-0" size={18} />
            <h2 className="text-base font-semibold text-ink">Near-expiry batch inventory</h2>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            <div className="flex items-center gap-2 text-xs">
              <Filter size={14} className="text-muted" />
              <label htmlFor="expiry-days-filter" className="font-semibold text-ink">
                Threshold window:
              </label>
              <select
                id="expiry-days-filter"
                aria-label="Filter expiry window"
                value={days}
                onChange={e => {
                  setDays(Number(e.target.value));
                  setPage(0);
                }}
                className="rounded-lg border border-border bg-canvas px-3 py-1.5 text-xs font-medium text-ink shadow-xs outline-none"
              >
                <option value={30}>Critical Expiry (≤ 30 Days)</option>
                <option value={60}>Warning Expiry (≤ 60 Days)</option>
                <option value={90}>Near Expiry (≤ 90 Days)</option>
                <option value={180}>6 Months Window (≤ 180 Days)</option>
                <option value={365}>1 Year Horizon (≤ 365 Days)</option>
              </select>
            </div>
          </div>
        </div>

        {/* Filter & Search Bar */}
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border p-3 text-xs">
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
                placeholder="Search medicine, batch, or warehouse depot…"
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

            <div className="flex h-10 items-center rounded-lg border border-border bg-surface-soft p-1 shrink-0">
              {(
                [
                  { id: "ALL", label: "All batches" },
                  { id: "CRITICAL", label: "Critical only (≤30d)" },
                  { id: "WARNING", label: "Warning (31–90d)" }
                ] as const
              ).map(t => {
                const isActive = severityFilter === t.id;
                return (
                  <button
                    key={t.id}
                    onClick={() => {
                      setSeverityFilter(t.id);
                      setPage(0);
                    }}
                    className={`h-full px-3.5 rounded-md text-xs font-semibold transition flex items-center justify-center ${
                      isActive
                        ? "bg-canvas text-ink shadow-xs border border-border/80"
                        : "text-muted hover:text-ink hover:bg-canvas/50"
                    }`}
                  >
                    {t.label}
                  </button>
                );
              })}
            </div>
          </div>

          <div className="flex h-10 items-center rounded-lg border border-border bg-surface-soft px-3.5 text-xs text-muted shrink-0">
            Showing&nbsp;<strong className="text-ink">{pageStart}–{pageEnd}</strong>&nbsp;of&nbsp;<strong className="text-ink">{totalItems}</strong>&nbsp;expiring batches
          </div>
        </div>

        {expiryQuery.isLoading ? (
          <Loading />
        ) : expiryQuery.isError ? (
          <ErrorBanner message={errorMessage(expiryQuery.error)} />
        ) : !paginatedItems.length ? (
          <div className="p-8 text-center">
            <Empty title="No batches nearing expiry within the selected threshold" />
            {(search || severityFilter !== "ALL") && (
              <button
                onClick={() => {
                  setSearch("");
                  setSeverityFilter("ALL");
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
                  <th className="px-4 py-3 min-w-[200px]">Medicine formulation</th>
                  <th className="px-4 py-3 min-w-[180px]">Warehouse depot</th>
                  <th className="px-4 py-3 w-[150px]">Batch / Lot</th>
                  <th className="px-4 py-3 w-[120px]">Expiry date</th>
                  <th className="px-4 py-3 w-[140px]">Days remaining</th>
                  <th className="px-4 py-3 text-right w-[110px]">Available</th>
                  <th className="px-4 py-3 text-right w-[110px]">Reserved</th>
                  <th className="px-4 py-3 w-[130px]">Risk status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {paginatedItems.map((item: ExpiryReportItem) => {
                  const isCritical = item.daysToExpiry <= 30;

                  return (
                    <tr
                      key={`${item.warehouseId}-${item.batchId}`}
                      className={`transition hover:bg-surface-soft/40 ${
                        isCritical ? "bg-danger-bg/10" : ""
                      }`}
                    >
                      <td className="px-4 py-3">
                        <strong className="text-sm font-semibold text-ink">{item.genericName}</strong>
                        <span className="mt-0.5 block font-mono text-[11px] text-muted">
                          SKU: {item.medicineSku}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <strong className="font-semibold text-ink">{item.warehouseName}</strong>
                        <span className="mt-0.5 block font-mono text-[11px] text-muted">
                          Code: {item.warehouseCode}
                        </span>
                      </td>
                      <td className="px-4 py-3 font-mono font-semibold text-ink">
                        {item.batchNumber}
                      </td>
                      <td className="px-4 py-3 font-mono text-muted">{item.expiryDate}</td>
                      <td className="px-4 py-3">
                        {isCritical ? (
                          <div className="flex items-center gap-1.5 font-bold text-danger">
                            <AlertOctagon size={14} className="shrink-0" />
                            <span>{item.daysToExpiry} days left</span>
                          </div>
                        ) : (
                          <div className="flex items-center gap-1.5 font-semibold text-warning">
                            <Clock3 size={14} className="shrink-0" />
                            <span>{item.daysToExpiry} days left</span>
                          </div>
                        )}
                      </td>
                      <td className="px-4 py-3 text-right font-mono font-semibold text-ink">
                        {item.availableQuantity} BOX
                      </td>
                      <td className="px-4 py-3 text-right font-mono text-muted">
                        {item.reservedQuantity} BOX
                      </td>
                      <td className="px-4 py-3">
                        <StatusBadge status={item.status} />
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
                className="rounded border border-border bg-canvas px-2.5 py-1 text-xs font-medium text-ink shadow-xs hover:bg-surface-soft disabled:opacity-40 disabled:cursor-not-allowed"
                aria-label="Next page"
              >
                <span>Next</span>
                <ChevronRight size={14} />
              </button>
            </div>
          </div>
        )}
      </div>
    </section>
  );
}