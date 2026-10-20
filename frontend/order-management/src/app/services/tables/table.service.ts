import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { TableResponse } from '../../models/pos/pos.models';

@Injectable({ providedIn: 'root' })
export class TableService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiUrl}/tables`;

  getTables(outletId?: string): Observable<TableResponse[]> {
    let params = new HttpParams();
    if (outletId && outletId.trim() && outletId !== 'all') {
      params = params.set('outletId', outletId.trim());
    }
    return this.http.get<TableResponse[]>(this.baseUrl, { params });
  }

  createTable(tableNumber: string, outletId?: string): Observable<TableResponse> {
    let params = new HttpParams().set('tableNumber', tableNumber);
    if (outletId && outletId.trim() && outletId !== 'all') {
      params = params.set('outletId', outletId.trim());
    }
    return this.http.post<TableResponse>(this.baseUrl, null, { params });
  }
}