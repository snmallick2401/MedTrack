import { useState, useEffect } from "react";
import { useQuery } from "@tanstack/react-query";
import { Boxes, Warehouse as WarehouseIcon, Filter } from "lucide-react";
import { useUiStore } from "../../store/uiStore";
import { inventoryApi } from "../../services/inventoryApi";
import { masterDataApi } from "../../services/masterDataApi";
import { ErrorBanner, Loading, Empty } from "../../components/feedback/States";
import { StatusBadge } from "../../components/ui/StatusBadge";
import { errorMessage } from "../../utils/errors";
import type { InventoryBalance } from "../../types/api";

export function InventoryPage() {
  const assignedWarehouseId = useUiStore(s => s.warehouseId);
  const [selectedWarehouseId, setSelectedWarehouseId] = useState(assignedWarehouseId ?? "");

  const warehousesQuery = useQuery({
    queryKey: ["warehousesList"],
    queryFn: () => masterDataApi.warehouses()
  });

  useEffect(() => {
    if (!selectedWarehouseId && warehousesQuery.data?.content.length) {
      const active = warehousesQuery.data.content.find(w => w.status === "ACTIVE") ?? warehousesQuery.data.content[0];
      setSelectedWarehouseId(active.id);
    }
  }, [warehousesQuery.data, selectedWarehouseId]);

  const balancesQuery = useQuery({
    queryKey: ["balances", selectedWarehouseId],
    queryFn: () => inventoryApi.balances(selectedWarehouseId),
    enabled: !!selectedWarehouseId
  });

  return (
    <section className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-ink">Inventory balances</h1>
          <p className="text-muted">3-bucket real-time inventory balances (Available, Reserved, Quarantined).</p>
        </div>
        <div className="flex items-center gap-2">
          <WarehouseIcon size={18} className="text-muted" />
          <label htmlFor="warehouse-selector" className="text-sm font-medium text-ink">Warehouse:</label>
          <select
            id="warehouse-selector"
            value={selectedWarehouseId}
            onChange={e => setSelectedWarehouseId(e.target.value)}
            className="input min-w-[200px]"
          >
            {warehousesQuery.data?.content.map(w => (
              <option key={w.id} value={w.id}>
                {w.code} — {w.name}
              </option>
            ))}
          </select>
        </div>
      </div>

      {!selectedWarehouseId ? (
        <Loading />
      ) : balancesQuery.isLoading ? (
        <Loading />
      ) : balancesQuery.isError ? (
        <ErrorBanner message={errorMessage(balancesQuery.error)} />
      ) : !balancesQuery.data?.content.length ? (
        <Empty title="No inventory balances found for this warehouse" />
      ) : (
        <div className="rounded-lg border border-border bg-canvas shadow-sm">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-border bg-surface-soft text-xs uppercase text-muted">
                <tr>
                  <th className="p-3">Batch ID</th>
                  <th className="p-3">Storage Location</th>
                  <th className="p-3 text-right">Available</th>
                  <th className="p-3 text-right">Reserved</th>
                  <th className="p-3 text-right">Quarantined</th>
                  <th className="p-3 text-right font-semibold">Physical Total</th>
                  <th className="p-3">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {balancesQuery.data.content.map((b: InventoryBalance) => (
                  <tr key={b.id} className="hover:bg-surface-soft/50">
                    <td className="p-3 font-mono text-xs font-medium text-ink">{b.batchId}</td>
                    <td className="p-3 font-mono text-xs text-muted">{b.storageLocationId}</td>
                    <td className="p-3 text-right font-mono font-medium text-success">{b.availableQuantity}</td>
                    <td className="p-3 text-right font-mono text-muted">{b.reservedQuantity}</td>
                    <td className="p-3 text-right font-mono text-danger">{b.quarantinedQuantity}</td>
                    <td className="p-3 text-right font-mono font-bold text-ink">{b.physicalQuantity}</td>
                    <td className="p-3">
                      <StatusBadge
                        status={
                          b.physicalQuantity === 0
                            ? "OUT_OF_STOCK"
                            : b.availableQuantity === 0
                            ? "RESERVED"
                            : b.availableQuantity < 10
                            ? "LOW_STOCK"
                            : "IN_STOCK"
                        }
                      />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </section>
  );
}