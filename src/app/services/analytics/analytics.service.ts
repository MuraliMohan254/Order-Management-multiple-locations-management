import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { SalesDashboardResponse, BusinessInsightsResponse } from '../../models/analytics.models';

@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiUrl}/analytics`;

  getDashboardMetrics(startDate?: string, endDate?: string): Observable<SalesDashboardResponse> {
    let params = new HttpParams();
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);
    return this.http.get<SalesDashboardResponse>(`${this.baseUrl}/dashboard`, { params });
  }

  getBusinessInsights(startDate?: string, endDate?: string): Observable<BusinessInsightsResponse> {
    let params = new HttpParams();
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);
    return this.http.get<BusinessInsightsResponse>(`${this.baseUrl}/business-insights`, { params });
  }

  downloadSalesReportPdf(startDate?: string, endDate?: string): Observable<Blob> {
    let params = new HttpParams();
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);
    return this.http.get(`${this.baseUrl}/reports/sales/pdf`, {
      params,
      responseType: 'blob'
    });
  }
}
