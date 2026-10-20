import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, from, of } from 'rxjs';
import { switchMap, tap } from 'rxjs/operators';
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
  createTakeAwayOrder(items: OrderItemRequest[], outletId?: string, discount?: number): Observable<OrderResponse> {
    let params = new HttpParams();
    if (outletId && outletId.trim() && outletId !== 'all') {
      params = params.set('outletId', outletId.trim());
    }
    if (discount !== undefined && discount !== null) {
      params = params.set('discount', discount.toString());
    }
    return this.http.post<OrderResponse>(`${this.baseUrl}/take-away`, items, { params });
  }

  createPosDineInOrder(tableId: string, items: OrderItemRequest[], discount?: number): Observable<OrderResponse> {
    let params = new HttpParams().set('tableId', tableId);
    if (discount !== undefined && discount !== null) {
      params = params.set('discount', discount.toString());
    }
    return this.http.post<OrderResponse>(`${this.baseUrl}/dine-in`, items, { params });
  }

  appendItemsToOrder(orderId: string, items: OrderItemRequest[], discount?: number): Observable<OrderResponse> {
    let params = new HttpParams();
    if (discount !== undefined && discount !== null) {
      params = params.set('discount', discount.toString());
    }
    return this.http.post<OrderResponse>(`${this.baseUrl}/${orderId}/items`, items, { params });
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
    if (discount !== undefined && discount !== null) {
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

  // Cached configuration for printer IP and store details
  private cachedConfig: any = null;

  /**
   * Resolves the printer IP and Port for this specific physical terminal.
   * Priority: Local Terminal Storage -> Electron config.json -> Database -> Hardcoded fallback
   */
  getLocalPrinterSettings(): { ip: string; port: number } {
    const savedIp = typeof localStorage !== 'undefined' ? localStorage.getItem('LOCAL_PRINTER_IP') : null;
    const savedPort = typeof localStorage !== 'undefined' ? localStorage.getItem('LOCAL_PRINTER_PORT') : null;
    const electronPrinter = (typeof window !== 'undefined') ? (window as any).electronAPI?.appInfo?.config?.printer : null;

    return {
      ip: (savedIp && savedIp.trim()) ? savedIp.trim() : (electronPrinter?.defaultIp || this.cachedConfig?.printerIp || '192.168.31.53'),
      port: (savedPort && Number(savedPort) > 0) ? Number(savedPort) : (electronPrinter?.defaultPort || this.cachedConfig?.printerPort || 9100)
    };
  }

  // --- Direct Network Thermal Printing (ESC/POS) ---
  printTestReceipt(ip?: string, port?: number): Observable<{ success: boolean; message: string }> {
    const localPrinter = this.getLocalPrinterSettings();
    const targetIp = (ip && ip.trim()) ? ip.trim() : localPrinter.ip;
    const targetPort = (port && port > 0) ? port : localPrinter.port;

    const electron = typeof window !== 'undefined' ? (window as any).electronAPI : null;
    if (electron && electron.isElectron) {
      return from(electron.printTestReceipt(targetIp, targetPort, this.cachedConfig) as Promise<{ success: boolean; message: string }>);
    }

    return of({
      success: false,
      message: 'Direct TCP thermal printing requires the Electron desktop application.'
    });
  }

  printThermalKot(orderOrId: string | OrderResponse, batchNumber?: number): Observable<{ success: boolean; message: string }> {
    const electron = typeof window !== 'undefined' ? (window as any).electronAPI : null;
    if (electron && electron.isElectron) {
      const getOrder$ = typeof orderOrId === 'string' ? this.getOrderById(orderOrId) : of(orderOrId);
      return getOrder$.pipe(
        switchMap(order => {
          const localPrinter = this.getLocalPrinterSettings();
          const targetIp = localPrinter.ip;
          const targetPort = localPrinter.port;
          return from(electron.printKitchenOrderTicket(targetIp, targetPort, order, batchNumber, this.cachedConfig) as Promise<{ success: boolean; message: string }>);
        })
      );
    }

    return of({
      success: false,
      message: 'Browser mode: Thermal ESC/POS printing requires Electron app.'
    });
  }

  printThermalBill(orderOrId: string | OrderResponse): Observable<{ success: boolean; message: string }> {
    const electron = typeof window !== 'undefined' ? (window as any).electronAPI : null;
    if (electron && electron.isElectron) {
      const getOrder$ = typeof orderOrId === 'string' ? this.getOrderById(orderOrId) : of(orderOrId);
      return getOrder$.pipe(
        switchMap(order => {
          const localPrinter = this.getLocalPrinterSettings();
          const targetIp = localPrinter.ip;
          const targetPort = localPrinter.port;
          const settledBy = (order as any).settledBy || '';
          return from(electron.printCustomerBill(targetIp, targetPort, order, settledBy, this.cachedConfig) as Promise<{ success: boolean; message: string }>);
        })
      );
    }

    return of({
      success: false,
      message: 'Browser mode: Thermal ESC/POS printing requires Electron app.'
    });
  }

  getAppConfig(): Observable<{ taxRate: number; taxName: string; restaurantName: string; defaultDiscountPercentage: number; kitchenWorkflowEnabled: boolean; thermalPrintingEnabled: boolean; thermalPaperSize: string; printerName: string | null; gstNumber?: string; printerIp?: string; printerPort?: number; quickBillPrintReceipt?: boolean; }> {
    return this.http.get<{ taxRate: number; taxName: string; restaurantName: string; defaultDiscountPercentage: number; kitchenWorkflowEnabled: boolean; thermalPrintingEnabled: boolean; thermalPaperSize: string; printerName: string | null; gstNumber?: string; printerIp?: string; printerPort?: number; quickBillPrintReceipt?: boolean; }>(`${environment.apiUrl}/public/config`).pipe(
      tap(cfg => {
        if (cfg) this.cachedConfig = cfg;
      })
    );
  }

  updateAppConfig(config: any): Observable<any> {
    return this.http.put<any>(`${environment.apiUrl}/config`, config).pipe(
      tap(() => {
        if (!this.cachedConfig) this.cachedConfig = {};
        Object.assign(this.cachedConfig, config);
      })
    );
  }

  clearKitchen(): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/kitchen/clear`, {});
  }

  getKitchenEventSource(outletId?: string): EventSource {
    const token = sessionStorage.getItem('jwt_token') || '';
    let url = `${this.baseUrl}/kitchen/stream?token=${encodeURIComponent(token)}`;
    if (outletId && outletId.trim() && outletId !== 'all') {
      url += `&outletId=${encodeURIComponent(outletId.trim())}`;
    }
    return new EventSource(url);
  }
}