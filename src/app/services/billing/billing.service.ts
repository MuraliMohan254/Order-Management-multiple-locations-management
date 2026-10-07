import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { OrderResponse } from '../../models/pos/pos.models';

@Injectable({ providedIn: 'root' })
export class BillingService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiUrl}/orders`;

  // Generic fetch with optional status filtering
  getOrders(statuses?: string[]): Observable<OrderResponse[]> {
    let params = new HttpParams();
    if (statuses && statuses.length > 0) {
      // Append allows multiple parameters with the same key (e.g., ?statuses=CONFIRMED&statuses=BILLED)
      statuses.forEach(status => {
        params = params.append('statuses', status);
      });
    }
    return this.http.get<OrderResponse[]>(this.baseUrl, { params });
  }

  getOrderById(orderId: string): Observable<OrderResponse> {
    return this.http.get<OrderResponse>(`${this.baseUrl}/${orderId}`);
  }

  generateBill(orderId: string, discount: number = 0): Observable<OrderResponse> {
    let params = new HttpParams();
    if (discount > 0) params = params.set('discount', discount.toString());
    return this.http.post<OrderResponse>(`${this.baseUrl}/${orderId}/bill`, null, { params });
  }

  settlePayment(orderId: string, method: string, amount: number): Observable<PaymentResponse> {
    const params = new HttpParams().set('method', method).set('amount', amount.toString());
    return this.http.post<PaymentResponse>(`${this.baseUrl}/${orderId}/pay`, null, { params });
  }
}