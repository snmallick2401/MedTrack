export type Role =
  | "SUPER_ADMIN"
  | "CENTRAL_WAREHOUSE_MANAGER"
  | "STORE_MANAGER"
  | "LOGISTICS_COORDINATOR"
  | "AUDITOR";

export interface User {
  id: string;
  email: string;
  fullName: string;
  role: Role;
  assignedWarehouseId?: string | null;
}

export interface AuthResponse {
  accessToken: string;
  tokenType?: string;
  expiresIn: number;
  email: string;
  role: Role;
  user?: User;
}

export interface ProblemDetail {
  status: number;
  detail: string;
  code: string;
  title?: string;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface InventoryBalance {
  id: string;
  warehouseId: string;
  batchId: string;
  storageLocationId: string;
  availableQuantity: number;
  reservedQuantity: number;
  quarantinedQuantity: number;
  physicalQuantity: number;
}

export interface TrackingEvent {
  status: string;
  locationName: string;
  latitude?: number;
  longitude?: number;
  remarks?: string;
  timestamp: string;
}

export interface TrackingResponse {
  shipmentId: string;
  events: TrackingEvent[];
}

export interface Medicine {
  id: string;
  sku: string;
  genericName: string;
  brandName?: string;
  categoryCode: string;
  dosageForm: string;
  strength: string;
  unitOfMeasure: string;
  storageTemp?: string;
  minStockThreshold: number;
  minReceivingShelfLifeDays: number;
  status: string;
  description?: string;
}

export interface Supplier {
  id: string;
  name: string;
  code: string;
  contactEmail?: string;
  contactPhone?: string;
  address?: string;
}

export interface Batch {
  id: string;
  batchNumber: string;
  medicineId: string;
  supplierId: string;
  manufacturingDate: string;
  expiryDate: string;
  initialQuantity: number;
  status: string;
}

export interface Warehouse {
  id: string;
  code: string;
  name: string;
  type: string;
  address: string;
  status: string;
}

export interface StorageLocation {
  id: string;
  zone: string;
  rack: string;
  shelf: string;
  binCode: string;
}

export interface Notification {
  id: string;
  type: string;
  title: string;
  message: string;
  entityType?: string;
  entityId?: string;
  warehouseId?: string;
  createdAt: string;
  readAt?: string;
}

export interface AuditLog {
  id: string;
  userId?: string;
  action: string;
  entityName: string;
  entityId?: string;
  changesJson?: string;
  createdAt: string;
}

export interface InboundReceipt {
  batchId: string;
  inventoryBalanceId: string;
  journalEntryNumber: string;
  availableQuantity: number;
}

export interface TransferItem {
  batchId?: string;
  medicineId: string;
  requestedQuantity: number;
  allocatedQuantity: number;
  pickedQuantity: number;
  dispatchedQuantity: number;
  receivedQuantity: number;
  damagedQuantity: number;
}

export interface Transfer {
  id: string;
  transferNumber: string;
  status: string;
  sourceWarehouseId: string;
  destinationWarehouseId: string;
  items: TransferItem[];
}

export interface ShipmentItem {
  shipmentItemId: string;
  transferItemId: string;
  batchId?: string;
  batchNumber?: string;
  medicineId?: string;
  medicineName?: string;
  quantity: number;
}

export interface Shipment {
  id: string;
  shipmentNumber: string;
  status: string;
  transferId: string;
  carrierName: string;
  trackingNumber: string;
  items: ShipmentItem[];
}