import { api } from "./apiClient";
import type { Batch, Medicine, Page, StorageLocation, Supplier, Warehouse } from "../types/api";

export type MedicinePayload = Omit<Medicine, "id" | "categoryCode"> & { categoryId: string };
export type BatchPayload = Omit<Batch, "id">;
export type WarehousePayload = {
  code: string;
  name: string;
  type: string;
  address: string;
  latitude?: number;
  longitude?: number;
  contactPhone?: string;
  status: string;
};
export type SupplierPayload = Omit<Supplier, "id">;

export const masterDataApi = {
  categories: () =>
    api.get<Array<{ id: string; code: string; name: string }>>("/medicine-categories").then(r => r.data),
  medicines: (page = 0, search = "") =>
    api.get<Page<Medicine>>("/medicines", { params: { page, size: 50, search: search || undefined } }).then(r => r.data),
  createMedicine: (body: MedicinePayload) =>
    api.post<Medicine>("/medicines", body).then(r => r.data),
  updateMedicine: (id: string, body: MedicinePayload) =>
    api.put<Medicine>(`/medicines/${id}`, body).then(r => r.data),
  batches: (page = 0) =>
    api.get<Page<Batch>>("/batches", { params: { page, size: 50 } }).then(r => r.data),
  createBatch: (body: BatchPayload) =>
    api.post<Batch>("/batches", body).then(r => r.data),
  suppliers: (page = 0) =>
    api.get<Page<Supplier>>("/suppliers", { params: { page, size: 50 } }).then(r => r.data),
  createSupplier: (body: SupplierPayload) =>
    api.post<Supplier>("/suppliers", body).then(r => r.data),
  warehouses: (page = 0) =>
    api.get<Page<Warehouse>>("/warehouses", { params: { page, size: 50 } }).then(r => r.data),
  createWarehouse: (body: WarehousePayload) =>
    api.post<Warehouse>("/warehouses", body).then(r => r.data),
  storageLocations: (warehouseId: string) =>
    api.get<StorageLocation[]>(`/warehouses/${warehouseId}/storage-locations`).then(r => r.data),
  createStorageLocation: (warehouseId: string, body: Omit<StorageLocation, "id">) =>
    api.post<StorageLocation>(`/warehouses/${warehouseId}/storage-locations`, body).then(r => r.data)
};