import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { SidebarComponent } from '../sidebar/sidebar';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [CommonModule, RouterModule,SidebarComponent],
  templateUrl: 'layout.html',
})
export class LayoutComponent {
  private authService = inject(AuthService);

  isCollapsed = signal(false);
  isMobileOpen = signal(false);

  username = signal<string>(this.authService.getUsername());
  userRole = signal<string>(this.authService.getUserRole());

  getUserInitials(): string {
    const name = this.username();
    return name ? name.substring(0, 2).toUpperCase() : 'PR';
  }

  logout() {
    this.authService.logout();
  }
}