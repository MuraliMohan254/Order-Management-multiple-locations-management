import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { SalesDashboardResponse, BusinessInsightsResponse } from '../../models/analytics.models';

@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiUrl}/analytics`;

  getDashboardMetrics(startDate?: string, endDate?: string, outletId?: string): Observable<SalesDashboardResponse> {
    let params = new HttpParams();
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);
    if (outletId) params = params.set('outletId', outletId);
    return this.http.get<SalesDashboardResponse>(`${this.baseUrl}/dashboard`, { params });
  }

  getBusinessInsights(startDate?: string, endDate?: string, outletId?: string): Observable<BusinessInsightsResponse> {
    let params = new HttpParams();
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);
    if (outletId) params = params.set('outletId', outletId);
    return this.http.get<BusinessInsightsResponse>(`${this.baseUrl}/business-insights`, { params });
  }

  downloadSalesReportPdf(startDate?: string, endDate?: string, outletId?: string): Observable<Blob> {
    let params = new HttpParams();
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);
    if (outletId) params = params.set('outletId', outletId);
    return this.http.get(`${this.baseUrl}/reports/sales/pdf`, {
      params,
      responseType: 'blob'
    });
  }

  downloadSalesSummaryPdf(startDate?: string, endDate?: string, outletId?: string): Observable<Blob> {
    let params = new HttpParams();
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);
    if (outletId) params = params.set('outletId', outletId);
    return this.http.get(`${this.baseUrl}/reports/summary/pdf`, {
      params,
      responseType: 'blob'
    });
  }

  downloadDailyOutletReportPdf(startDate?: string, endDate?: string, outletId?: string): Observable<Blob> {
    let params = new HttpParams();
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);
    if (outletId) params = params.set('outletId', outletId);
    return this.http.get(`${this.baseUrl}/reports/multi-outlet/daily/pdf`, {
      params,
      responseType: 'blob'
    });
  }

  downloadMonthlyOutletReportPdf(startDate?: string, endDate?: string, outletId?: string): Observable<Blob> {
    let params = new HttpParams();
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);
    if (outletId) params = params.set('outletId', outletId);
    return this.http.get(`${this.baseUrl}/reports/multi-outlet/monthly/pdf`, {
      params,
      responseType: 'blob'
    });
  }

  // ==========================================
  // EXCEL (XLSX) DOWNLOAD METHODS
  // ==========================================

  downloadSalesReportXlsx(startDate?: string, endDate?: string, outletId?: string): Observable<Blob> {
    let params = new HttpParams();
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);
    if (outletId) params = params.set('outletId', outletId);
    return this.http.get(`${this.baseUrl}/reports/sales/xlsx`, {
      params,
      responseType: 'blob'
    });
  }

  downloadSalesSummaryXlsx(startDate?: string, endDate?: string, outletId?: string): Observable<Blob> {
    let params = new HttpParams();
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);
    if (outletId) params = params.set('outletId', outletId);
    return this.http.get(`${this.baseUrl}/reports/summary/xlsx`, {
      params,
      responseType: 'blob'
    });
  }

  downloadDailyOutletReportXlsx(startDate?: string, endDate?: string, outletId?: string): Observable<Blob> {
    let params = new HttpParams();
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);
    if (outletId) params = params.set('outletId', outletId);
    return this.http.get(`${this.baseUrl}/reports/multi-outlet/daily/xlsx`, {
      params,
      responseType: 'blob'
    });
  }

  downloadMonthlyOutletReportXlsx(startDate?: string, endDate?: string, outletId?: string): Observable<Blob> {
    let params = new HttpParams();
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);
    if (outletId) params = params.set('outletId', outletId);
    return this.http.get(`${this.baseUrl}/reports/multi-outlet/monthly/xlsx`, {
      params,
      responseType: 'blob'
    });
  }
}
