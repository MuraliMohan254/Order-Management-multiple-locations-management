import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { OrderResponse, PagedResponse, PaymentResponse } from '../../models/pos/pos.models';

@Injectable({ providedIn: 'root' })
export class BillingService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiUrl}/orders`;

  // Backend paginated fetch with search, filter, and pageable controls
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

  // Generic fetch with optional status filtering
  getOrders(statuses?: string[], outletId?: string): Observable<OrderResponse[]> {
    let params = new HttpParams();
    if (statuses && statuses.length > 0) {
      // Append allows multiple parameters with the same key (e.g., ?statuses=CONFIRMED&statuses=BILLED)
      statuses.forEach(status => {
        params = params.append('statuses', status);
      });
    }
    if (outletId && outletId.trim() && outletId !== 'all') {
      params = params.set('outletId', outletId.trim());
    }
    return this.http.get<OrderResponse[]>(this.baseUrl, { params });
  }

  getOrderById(orderId: string): Observable<OrderResponse> {
    return this.http.get<OrderResponse>(`${this.baseUrl}/${orderId}`);
  }

  generateBill(orderId: string, discount?: number): Observable<OrderResponse> {
    let params = new HttpParams();
    if (discount !== undefined && discount !== null) {
      params = params.set('discount', discount.toString());
    }
    return this.http.post<OrderResponse>(`${this.baseUrl}/${orderId}/bill`, null, { params });
  }

  settlePayment(orderId: string, method: string, amount: number): Observable<PaymentResponse> {
    const params = new HttpParams().set('method', method).set('amount', amount.toString());
    return this.http.post<PaymentResponse>(`${this.baseUrl}/${orderId}/pay`, null, { params });
  }

  mergeOrders(targetOrderId: string, sourceOrderId: string): Observable<OrderResponse> {
    const params = new HttpParams().set('sourceOrderId', sourceOrderId);
    return this.http.post<OrderResponse>(`${this.baseUrl}/${targetOrderId}/merge`, null, { params });
  }
}