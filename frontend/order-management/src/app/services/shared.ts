import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root',
})
export class SharedService {
  private http = inject(HttpClient);
  appName: string = 'WildBar';

  constructor() {
    this.refreshAppName();
  }

  refreshAppName() {
    try {
      this.http.get<{ restaurantName?: string }>(`${environment.apiUrl}/public/config`).subscribe({
        next: (cfg) => {
          if (cfg && cfg.restaurantName && cfg.restaurantName.trim()) {
            this.appName = cfg.restaurantName.trim();
          }
        },
        error: () => {
          // Keep default 'WildBar'
        }
      });
    } catch (_) {}
  }

  setAppName(name: string) {
    if (name && name.trim()) {
      this.appName = name.trim();
    }
  }
}
