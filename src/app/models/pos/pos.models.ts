export interface OrderItemRequest {
  itemId: string; //
  quantity: number; //[cite: 3]
  notes?: string; //[cite: 3]
}

export interface OrderResponse {
  id: string; //
  orderNumber: string; //[cite: 4]
  orderType: string; //[cite: 4]
  status: string; //[cite: 4]
  subTotal: number; //[cite: 4]
  taxAmount: number; //[cite: 4]
  discountAmount: number; //[cite: 4]
  grandTotal: number; //[cite: 4]
}

export interface TableResponse {
  id: string; //[cite: 7]
  tableNumber: string; //[cite: 7]
  qrToken: string; //[cite: 7]
  status: string; //[cite: 7]
}

export interface PaymentResponse {
  id: string; //[cite: 5]
  orderId: string; //[cite: 5]
  amountPaid: number; //[cite: 5]
  paymentMethod: string; //[cite: 5]
  paymentStatus: string; //[cite: 5]
}

// Internal UI Cart Model (Not sent to backend directly)
export interface CartItem {
  itemId: string;
  name: string;
  price: number;
  quantity: number;
  notes?: string;
}