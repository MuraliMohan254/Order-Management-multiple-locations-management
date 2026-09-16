import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { CategoryRequest, CategoryResponse, ItemRequest, ItemResponse } from '../models/menu.models';

@Injectable({ providedIn: 'root' })
export class MenuService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiUrl}/menu`;

  // --- Categories ---
  getCategories(search?: string | null, active: boolean = true): Observable<CategoryResponse[]> {
    let params = new HttpParams().set('active', active);
    if (search) params = params.set('search', search);
    return this.http.get<CategoryResponse[]>(`${this.baseUrl}/categories`, { params });
  }

  createCategory(request: CategoryRequest): Observable<CategoryResponse> {
    return this.http.post<CategoryResponse>(`${this.baseUrl}/categories`, request);
  }

  updateCategory(id: string, request: CategoryRequest): Observable<CategoryResponse> {
    return this.http.put<CategoryResponse>(`${this.baseUrl}/categories/${id}`, request);
  }

  deleteCategory(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/categories/${id}`);
  }

  // --- Items ---
  getItemsByCategory(categoryId: string, search?: string | null): Observable<ItemResponse[]> {
    let params = new HttpParams();
    if (search) params = params.set('search', search);
    return this.http.get<ItemResponse[]>(`${this.baseUrl}/categories/${categoryId}/items`, { params });
  }

  createItem(request: ItemRequest): Observable<ItemResponse> {
    return this.http.post<ItemResponse>(`${this.baseUrl}/items`, request);
  }

  updateItem(id: string, request: ItemRequest): Observable<ItemResponse> {
    return this.http.put<ItemResponse>(`${this.baseUrl}/items/${id}`, request);
  }

  deleteItem(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/items/${id}`);
  }
}