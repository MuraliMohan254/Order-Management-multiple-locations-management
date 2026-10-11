export interface OrderItemRequest {
  itemId: string;
  quantity: number;
  notes?: string;
}

export interface OrderItemDetailResponse {
  id: string;
  itemId: string;
  itemName: string;
  quantity: number;
  unitPrice: number;
  totalPrice: number;
  notes?: string;
}

export interface OrderResponse {
  id: string;
  orderNumber: string;
  orderType: 'DINE_IN' | 'TAKE_AWAY' | string;
  status: 'SOFT_ORDER' | 'CONFIRMED' | 'PREPARING' | 'READY' | 'SERVED' | 'BILLED' | 'CANCELLED' | string;
  kitchenStatus?: string;
  tableId?: string;
  tableNumber?: string;
  subTotal: number;
  taxAmount: number;
  discountAmount: number;
  grandTotal: number;
  createdAt?: string;
  outletId?: string;
  outletName?: string;
  settledById?: number;
  settledBy?: string;
  items?: OrderItemDetailResponse[];
}

export interface PagedResponse<T> {
  content: T[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
  first: boolean;
  empty: boolean;
}

export interface TableResponse {
  id: string;
  tableNumber: string;
  qrToken: string;
  status: 'AVAILABLE' | 'OCCUPIED' | string;
  outletId?: string;
  outletName?: string;
}

export interface PaymentResponse {
  id: string;
  orderId: string;
  amountPaid: number;
  paymentMethod: string;
  paymentStatus: string;
  settledById?: number;
  settledBy?: string;
}

export interface KitchenTicketResponse {
  orderItemId: string;
  orderId?: string;
  orderNumber: string;
  tableName: string;
  itemName: string;
  quantity: number;
  notes?: string;
  orderTime: string;
  status?: string;
}

// Internal UI Cart Model
export interface CartItem {
  itemId: string;
  name: string;
  price: number;
  quantity: number;
  notes?: string;
}