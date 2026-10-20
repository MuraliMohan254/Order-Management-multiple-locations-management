import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);

  isAuthenticated = signal<boolean>(this.hasToken());

  private hasToken(): boolean {
    return !!sessionStorage.getItem('jwt_token');
  }

  private decodeToken(): any {
    const token = sessionStorage.getItem('jwt_token');
    if (!token) return null;
    try {
      const payload = token.split('.')[1];
      // Decode base64url safely
      const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
      const jsonPayload = decodeURIComponent(
        atob(base64)
          .split('')
          .map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
          .join('')
      );
      return JSON.parse(jsonPayload);
    } catch (e) {
      return null;
    }
  }

  getUsername(): string {
    const decoded = this.decodeToken();
    return decoded?.username || decoded?.sub || 'User';
  }

  getUserRole(): string {
    const decoded = this.decodeToken();
    if (decoded?.roles && Array.isArray(decoded.roles)) {
      // Filter out system factors and grab the highest level functional role (e.g., ADMIN)
      const functionalRole = decoded.roles.find((r: string) => r !== 'FACTOR_PASSWORD');
      return functionalRole || decoded.roles[0] || 'USER';
    }
    return decoded?.role || 'USER';
  }

  isAdmin(): boolean {
    const role = (this.getUserRole() || '').toUpperCase();
    return role === 'ADMIN' || role === 'ROLE_ADMIN' || role === 'SUPER_ADMIN';
  }

  login(credentials: { username: string; password: string }): Observable<any> {
    return this.http.post<any>(`${environment.apiUrl}/login`, credentials).pipe(
      tap(response => {
        sessionStorage.setItem('jwt_token', response.token);
        this.isAuthenticated.set(true);
        this.router.navigate(['/dashboard']);
      })
    );
  }

  logout() {
    sessionStorage.removeItem('jwt_token');
    this.isAuthenticated.set(false);
    this.router.navigate(['/login']);
  }
}