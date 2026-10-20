import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface Outlet {
  id?: string;
  name: string;
  code: string;
  address: string;
  contactNumber: string;
  active: boolean;
  isMainOutlet?: boolean;
}

export interface OutletMonitoringItem {
  id: string;
  name: string;
  code: string;
  address: string;
  contactNumber: string;
  active: boolean;
  todaySales: number;
  todayOrders: number;
  activeOrders: number;
  totalTables: number;
  occupiedTables: number;
  averageOrderValue: number;
}

export interface MultiOutletMonitoringResponse {
  consolidatedTodaySales: number;
  consolidatedTodayOrders: number;
  consolidatedActiveOrders: number;
  consolidatedOccupiedTables: number;
  consolidatedTotalTables: number;
  totalOutlets: number;
  activeOutlets: number;
  outlets: OutletMonitoringItem[];
  pageNumber?: number;
  pageSize?: number;
  totalElements?: number;
  totalPages?: number;
  last?: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class OutletService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/outlets`;

  getAllOutlets(): Observable<Outlet[]> {
    return this.http.get<Outlet[]>(this.apiUrl);
  }

  getActiveOutlets(search?: string, limit?: number): Observable<Outlet[]> {
    let params = new HttpParams();
    if (search && search.trim()) params = params.set('search', search.trim());
    if (limit) params = params.set('limit', limit.toString());
    return this.http.get<Outlet[]>(`${this.apiUrl}/active`, { params });
  }

  getMonitoringSummary(page?: number, size?: number, search?: string, status?: string): Observable<MultiOutletMonitoringResponse> {
    let params = new HttpParams();
    if (page !== undefined && page !== null) params = params.set('page', page.toString());
    if (size !== undefined && size !== null) params = params.set('size', size.toString());
    if (search && search.trim()) params = params.set('search', search.trim());
    if (status && status !== 'ALL') params = params.set('status', status);
    return this.http.get<MultiOutletMonitoringResponse>(`${this.apiUrl}/monitoring`, { params });
  }

  createOutlet(outlet: Outlet): Observable<Outlet> {
    return this.http.post<Outlet>(this.apiUrl, outlet);
  }

  updateOutlet(id: string, outlet: Outlet): Observable<Outlet> {
    return this.http.put<Outlet>(`${this.apiUrl}/${id}`, outlet);
  }

  deleteOutlet(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
