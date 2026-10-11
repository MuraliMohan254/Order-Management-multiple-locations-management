import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { 
  OrderItemRequest, 
  OrderResponse, 
  TableResponse, 
  PaymentResponse, 
  KitchenTicketResponse,
  PagedResponse
} from '../../models/pos/pos.models';

@Injectable({ providedIn: 'root' })
export class PosService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiUrl}/orders`;

  // --- Orders Creation & Modification ---
  createTakeAwayOrder(items: OrderItemRequest[], outletId?: string): Observable<OrderResponse> {
    let params = new HttpParams();
    if (outletId && outletId.trim() && outletId !== 'all') {
      params = params.set('outletId', outletId.trim());
    }
    return this.http.post<OrderResponse>(`${this.baseUrl}/take-away`, items, { params });
  }

  createPosDineInOrder(tableId: string, items: OrderItemRequest[]): Observable<OrderResponse> {
    const params = new HttpParams().set('tableId', tableId);
    return this.http.post<OrderResponse>(`${this.baseUrl}/dine-in`, items, { params });
  }

  appendItemsToOrder(orderId: string, items: OrderItemRequest[]): Observable<OrderResponse> {
    return this.http.post<OrderResponse>(`${this.baseUrl}/${orderId}/items`, items);
  }

  getOrderById(orderId: string): Observable<OrderResponse> {
    return this.http.get<OrderResponse>(`${this.baseUrl}/${orderId}`);
  }

  getPagedOrders(options?: {
    statuses?: string[];
    orderType?: string;
    search?: string;
    outletId?: string;
    page?: number;
    size?: number;
    sortBy?: string;
    sortDirection?: string;
  }): Observable<PagedResponse<OrderResponse>> {
    let params = new HttpParams();
    if (options?.statuses && options.statuses.length > 0) {
      options.statuses.forEach(status => {
        params = params.append('statuses', status);
      });
    }
    if (options?.orderType) params = params.set('orderType', options.orderType);
    if (options?.search && options.search.trim()) params = params.set('search', options.search.trim());
    if (options?.outletId && options.outletId.trim() && options.outletId !== 'all') {
      params = params.set('outletId', options.outletId.trim());
    }
    if (options?.page !== undefined) params = params.set('page', options.page.toString());
    if (options?.size !== undefined) params = params.set('size', options.size.toString());
    if (options?.sortBy) params = params.set('sortBy', options.sortBy);
    if (options?.sortDirection) params = params.set('sortDirection', options.sortDirection);

    return this.http.get<PagedResponse<OrderResponse>>(`${this.baseUrl}/paged`, { params });
  }

  getOrders(statuses?: string[], outletId?: string): Observable<OrderResponse[]> {
    let params = new HttpParams();
    if (statuses && statuses.length > 0) {
      statuses.forEach(status => {
        params = params.append('statuses', status);
      });
    }
    if (outletId && outletId.trim() && outletId !== 'all') {
      params = params.set('outletId', outletId.trim());
    }
    return this.http.get<OrderResponse[]>(this.baseUrl, { params });
  }

  updateOrderStatus(orderId: string, status: string): Observable<OrderResponse> {
    const params = new HttpParams().set('status', status);
    return this.http.put<OrderResponse>(`${this.baseUrl}/${orderId}/status`, { status }, { params });
  }

  generateBill(orderId: string, discount?: number): Observable<OrderResponse> {
    let params = new HttpParams();
    if (discount !== undefined && discount > 0) {
      params = params.set('discount', discount.toString());
    }
    return this.http.post<OrderResponse>(`${this.baseUrl}/${orderId}/bill`, null, { params });
  }

  settlePayment(orderId: string, method: string, amount: number): Observable<PaymentResponse> {
    const params = new HttpParams()
      .set('method', method)
      .set('amount', amount.toString());
    return this.http.post<PaymentResponse>(`${this.baseUrl}/${orderId}/pay`, null, { params });
  }

  voidOrderItem(orderId: string, orderItemId: string): Observable<OrderResponse> {
    return this.http.delete<OrderResponse>(`${this.baseUrl}/${orderId}/items/${orderItemId}`);
  }

  cancelOrder(orderId: string): Observable<OrderResponse> {
    return this.http.put<OrderResponse>(`${this.baseUrl}/${orderId}/cancel`, null);
  }

  mergeOrders(targetOrderId: string, sourceOrderId: string): Observable<OrderResponse> {
    const params = new HttpParams().set('sourceOrderId', sourceOrderId);
    return this.http.post<OrderResponse>(`${this.baseUrl}/${targetOrderId}/merge`, null, { params });
  }

  // --- Kitchen Display System ---
  getActiveKitchenTickets(): Observable<KitchenTicketResponse[]> {
    return this.http.get<KitchenTicketResponse[]>(`${this.baseUrl}/kitchen/active`);
  }

  // --- Table Management ---
  createTable(tableNumber: string, outletId?: string): Observable<TableResponse> {
    let params = new HttpParams().set('tableNumber', tableNumber);
    if (outletId && outletId.trim() && outletId !== 'all') {
      params = params.set('outletId', outletId.trim());
    }
    return this.http.post<TableResponse>(`${environment.apiUrl}/tables`, null, { params });
  }

  getTables(outletId?: string): Observable<TableResponse[]> {
    let params = new HttpParams();
    if (outletId && outletId.trim() && outletId !== 'all') {
      params = params.set('outletId', outletId.trim());
    }
    return this.http.get<TableResponse[]>(`${environment.apiUrl}/tables`, { params });
  }

  // --- Public Customer QR Ordering APIs ---
  getTableByQrToken(qrToken: string): Observable<TableResponse> {
    return this.http.get<TableResponse>(`${environment.apiUrl}/public/table/${qrToken}`);
  }

  getActiveOrderByQrToken(qrToken: string): Observable<OrderResponse> {
    return this.http.get<OrderResponse>(`${environment.apiUrl}/public/table/${qrToken}/active-order`);
  }

  processQrOrder(qrToken: string, items: OrderItemRequest[]): Observable<OrderResponse> {
    return this.http.post<OrderResponse>(`${environment.apiUrl}/public/qr-order/${qrToken}`, items);
  }

  getPublicOrderById(orderId: string): Observable<OrderResponse> {
    return this.http.get<OrderResponse>(`${environment.apiUrl}/public/orders/${orderId}`);
  }

  getAppConfig(): Observable<{ taxRate: number; taxName: string; restaurantName: string; defaultDiscountPercentage: number; kitchenWorkflowEnabled: boolean; thermalPrintingEnabled: boolean; thermalPaperSize: string; printerName: string | null; }> {
    return this.http.get<{ taxRate: number; taxName: string; restaurantName: string; defaultDiscountPercentage: number; kitchenWorkflowEnabled: boolean; thermalPrintingEnabled: boolean; thermalPaperSize: string; printerName: string | null; }>(`${environment.apiUrl}/public/config`);
  }

  updateAppConfig(config: any): Observable<any> {
    return this.http.put<any>(`${environment.apiUrl}/config`, config);
  }

  clearKitchen(): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/kitchen/clear`, {});
  }
}