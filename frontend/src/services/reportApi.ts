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
  inventory: () =>
    api.get<Blob>("/reports/inventory", { responseType: "blob" }).then(r => r.data),
  expiryCsv: (days = 90) =>
    api.get<Blob>("/reports/expiry", { params: { days }, responseType: "blob" }).then(r => r.data),
  expiryData: (days = 90) =>
    api.get<ExpiryReportItem[]>("/reports/expiry/data", { params: { days } }).then(r => r.data)
};