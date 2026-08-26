import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Download, FileSpreadsheet, AlertTriangle, Filter } from "lucide-react";
import { reportApi, ExpiryReportItem } from "../../services/reportApi";
import { Loading, ErrorBanner, Empty } from "../../components/feedback/States";
import { StatusBadge } from "../../components/ui/StatusBadge";
import { errorMessage } from "../../utils/errors";

export function ReportsPage() {
  const [days, setDays] = useState(90);
  const [downloadingInv, setDownloadingInv] = useState(false);
  const [downloadingExp, setDownloadingExp] = useState(false);

  const expiryQuery = useQuery({
    queryKey: ["expiryReportData", days],
    queryFn: () => reportApi.expiryData(days)
  });

  async function downloadInventoryCsv() {
    try {
      setDownloadingInv(true);
      const blob = await reportApi.inventory();
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = "inventory.csv";
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
      a.download = `expiry_report_${days}d.csv`;
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
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-ink">Operational reports & exports</h1>
          <p className="text-muted">Authoritative CSV exports and near-expiry risk analysis.</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <button
            onClick={downloadInventoryCsv}
            disabled={downloadingInv}
            className="inline-flex min-h-10 items-center gap-2 rounded bg-ink px-4 text-sm font-medium text-white hover:opacity-90 disabled:opacity-50"
          >
            <Download size={16} />
            <span>{downloadingInv ? "Exporting…" : "Export inventory CSV"}</span>
          </button>
          <button
            onClick={downloadExpiryCsv}
            disabled={downloadingExp}
            className="inline-flex min-h-10 items-center gap-2 rounded border border-border bg-canvas px-4 text-sm font-medium text-ink hover:bg-surface-soft disabled:opacity-50"
          >
            <FileSpreadsheet size={16} />
            <span>{downloadingExp ? "Exporting…" : `Export expiry (${days}d) CSV`}</span>
          </button>
        </div>
      </div>

      <div className="rounded-lg border border-border bg-canvas p-5 shadow-sm">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <AlertTriangle className="text-warning" size={20} />
            <h2 className="text-lg font-semibold text-ink">Near-expiry batches</h2>
          </div>
          <div className="flex items-center gap-2 text-sm">
            <Filter size={16} className="text-muted" />
            <label htmlFor="expiry-days-filter" className="text-muted">Threshold:</label>
            <select
              id="expiry-days-filter" aria-label="Filter expiry window"
              value={days}
              onChange={e => setDays(Number(e.target.value))}
              className="rounded border border-border bg-canvas px-3 py-1.5 text-ink outline-none"
            >
              <option value={30}>Critical (≤ 30 days)</option>
              <option value={60}>Warning (≤ 60 days)</option>
              <option value={90}>Near Expiry (≤ 90 days)</option>
              <option value={180}>6 Months (≤ 180 days)</option>
              <option value={365}>1 Year (≤ 365 days)</option>
            </select>
          </div>
        </div>

        {expiryQuery.isLoading ? (
          <Loading />
        ) : expiryQuery.isError ? (
          <ErrorBanner message={errorMessage(expiryQuery.error)} />
        ) : !expiryQuery.data?.length ? (
          <Empty title="No batches nearing expiry within the selected threshold" />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-border bg-surface-soft text-xs uppercase text-muted">
                <tr>
                  <th className="p-3">Warehouse</th>
                  <th className="p-3">Medicine</th>
                  <th className="p-3">Batch</th>
                  <th className="p-3">Expiry date</th>
                  <th className="p-3">Days remaining</th>
                  <th className="p-3 text-right">Available</th>
                  <th className="p-3 text-right">Reserved</th>
                  <th className="p-3">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {expiryQuery.data.map((item: ExpiryReportItem) => (
                  <tr key={`${item.warehouseId}-${item.batchId}`} className="hover:bg-surface-soft/50">
                    <td className="p-3">
                      <span className="font-mono text-xs text-muted">{item.warehouseCode}</span>
                      <br />
                      <span className="font-medium text-ink">{item.warehouseName}</span>
                    </td>
                    <td className="p-3">
                      <span className="font-medium text-ink">{item.genericName}</span>
                      <br />
                      <span className="font-mono text-xs text-muted">{item.medicineSku}</span>
                    </td>
                    <td className="p-3 font-mono text-xs">{item.batchNumber}</td>
                    <td className="p-3 text-muted">{item.expiryDate}</td>
                    <td className="p-3">
                      <span className={`font-semibold ${item.daysToExpiry <= 30 ? "text-danger" : "text-warning"}`}>
                        {item.daysToExpiry} days
                      </span>
                    </td>
                    <td className="p-3 text-right font-mono font-medium text-ink">{item.availableQuantity}</td>
                    <td className="p-3 text-right font-mono text-muted">{item.reservedQuantity}</td>
                    <td className="p-3">
                      <StatusBadge status={item.status} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </section>
  );
}