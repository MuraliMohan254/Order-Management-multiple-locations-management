import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { TableResponse } from '../../models/pos/pos.models';

@Injectable({ providedIn: 'root' })
export class TableService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiUrl}/tables`;

  getTables(): Observable<TableResponse[]> {
    return this.http.get<TableResponse[]>(this.baseUrl);
  }

  createTable(tableNumber: string): Observable<TableResponse> {
    const params = new HttpParams().set('tableNumber', tableNumber);
    return this.http.post<TableResponse>(this.baseUrl, null, { params });
  }
}