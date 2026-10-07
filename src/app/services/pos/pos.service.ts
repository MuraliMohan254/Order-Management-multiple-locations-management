import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { OrderItemRequest, OrderResponse, TableResponse } from '../../models/pos/pos.models';

@Injectable({ providedIn: 'root' })
export class PosService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiUrl}/orders`; // Base route for orders

  // Create Walk-in / Take-Away Order
  createTakeAwayOrder(items: OrderItemRequest[]): Observable<OrderResponse> {
    return this.http.post<OrderResponse>(`${this.baseUrl}/take-away`, items);
  }

  // Create / Append to Table Order
  appendItemsToOrder(orderId: string, items: OrderItemRequest[]): Observable<OrderResponse> {
    return this.http.post<OrderResponse>(`${this.baseUrl}/${orderId}/items`, items);
  }

  createPosDineInOrder(tableId: string, items: OrderItemRequest[]): Observable<OrderResponse> {
    const params = new HttpParams().set('tableId', tableId);
    return this.http.post<OrderResponse>(`${this.baseUrl}/dine-in`, items, { params });
  }

  // Generate Bill
  generateBill(orderId: string, discount?: number): Observable<OrderResponse> {
    let params = new HttpParams();
    if (discount !== undefined && discount > 0) {
      params = params.set('discount', discount.toString());
    }
    return this.http.post<OrderResponse>(`${this.baseUrl}/${orderId}/bill`, null, { params });
  }

  // Settle Payment
  settlePayment(orderId: string, method: string, amount: number): Observable<PaymentResponse> {
    const params = new HttpParams()
      .set('method', method)
      .set('amount', amount.toString());
    return this.http.post<PaymentResponse>(`${this.baseUrl}/${orderId}/pay`, null, { params });
  }

  // Void an Item from Order
  voidOrderItem(orderId: string, orderItemId: string): Observable<OrderResponse> {
    return this.http.delete<OrderResponse>(`${this.baseUrl}/${orderId}/items/${orderItemId}`);
  }

  // Cancel Entire Order
  cancelOrder(orderId: string): Observable<OrderResponse> {
    return this.http.put<OrderResponse>(`${this.baseUrl}/${orderId}/cancel`, null);
  }
  
  // Table Management (Mapped from your TransactionController)
  createTable(tableNumber: string): Observable<TableResponse> {
    const params = new HttpParams().set('tableNumber', tableNumber);
    return this.http.post<TableResponse>(`${environment.apiUrl}/tables`, null, { params });
  }

  getTables(): Observable<TableResponse[]> {
    return this.http.get<TableResponse[]>(`${environment.apiUrl}/tables`);
  }
}