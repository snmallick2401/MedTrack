import { api } from "./apiClient";

export interface ExpiryReportItem {
  warehouseId: string;
  warehouseCode: string;
  warehouseName: string;
  medicineId: string;
  medicineSku: string;
  genericName: string;
  batchId: string;
  batchNumber: string;
  manufacturingDate: string;
  expiryDate: string;
  daysToExpiry: number;
  availableQuantity: number;
  reservedQuantity: number;
  quarantinedQuantity: number;
  physicalQuantity: number;
  status: string;
}

export const reportApi = {
  inventory: (warehouseId?: string) =>
    api.get<Blob>("/reports/inventory", { params: warehouseId ? { warehouseId } : {}, responseType: "blob" }).then(r => r.data),
  expiryCsv: (days = 90, warehouseId?: string) =>
    api.get<Blob>("/reports/expiry", { params: { days, ...(warehouseId ? { warehouseId } : {}) }, responseType: "blob" }).then(r => r.data),
  expiryData: (days = 90, warehouseId?: string) =>
    api.get<ExpiryReportItem[]>("/reports/expiry/data", { params: { days, ...(warehouseId ? { warehouseId } : {}) } }).then(r => r.data)
};