import { api } from "./apiClient";
import type { AuditLog, Notification, Page, Shipment, Transfer } from "../types/api";

const key = () => crypto.randomUUID();

export const operationsApi = {
  notifications: () => api.get<Notification[]>("/notifications").then(r => r.data),
  markNotificationRead: (id: string) => api.post(`/notifications/${id}/read`),
  evaluateNotifications: () => api.post<{ createdCount: number }>("/notifications/evaluate").then(r => r.data),
  audit: (page = 0) => api.get<{ content: AuditLog[] }>("/audit-logs", { params: { page, size: 50 } }).then(r => r.data),
  qr: (batchId: string) => api.get<{ dataUri: string }>(`/batches/${batchId}/barcode/qr`).then(r => r.data),
  code128: (batchId: string) => api.get<{ dataUri: string }>(`/batches/${batchId}/barcode/code128`).then(r => r.data),
  transfers: (page = 0) =>
    api.get<Page<Transfer>>("/stock-transfers", { params: { page, size: 50 } }).then(r => r.data),
  transfer: (id: string) => api.get<Transfer>(`/stock-transfers/${id}`).then(r => r.data),
  createTransfer: (body: unknown) =>
    api.post<Transfer>("/stock-transfers", body, { headers: { "X-Idempotency-Key": key() } }).then(r => r.data),
  approveTransfer: (id: string) => api.post<Transfer>(`/stock-transfers/${id}/approve`).then(r => r.data),
  allocateTransfer: (id: string) =>
    api.post<Transfer>(`/stock-transfers/${id}/allocate`, undefined, { headers: { "X-Idempotency-Key": key() } }).then(r => r.data),
  pickTransfer: (id: string, body: unknown) =>
    api.post<Transfer>(`/stock-transfers/${id}/pick`, body).then(r => r.data),
  packTransfer: (id: string) => api.post<Transfer>(`/stock-transfers/${id}/pack`).then(r => r.data),
  cancelTransfer: (id: string, reason: string) =>
    api.post<Transfer>(`/stock-transfers/${id}/cancel`, { reason }, { headers: { "X-Idempotency-Key": key() } }).then(r => r.data),
  shipments: (page = 0) =>
    api.get<Page<Shipment>>("/shipments", { params: { page, size: 50 } }).then(r => r.data),
  shipment: (id: string) => api.get<Shipment>(`/shipments/${id}`).then(r => r.data),
  createShipment: (body: unknown) => api.post<Shipment>("/shipments", body).then(r => r.data),
  dispatch: (transferId: string) =>
    api.post<Shipment>(`/stock-transfers/${transferId}/dispatch`, undefined, { headers: { "X-Idempotency-Key": key() } }).then(r => r.data),
  receive: (transferId: string, body: unknown) =>
    api.post(`/stock-transfers/${transferId}/receive`, body, { headers: { "X-Idempotency-Key": key() } })
};
