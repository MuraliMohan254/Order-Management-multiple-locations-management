import { SystemSettingsComponent } from './components/system-settings/system-settings';
import { Routes } from '@angular/router';
import { LoginComponent } from './components/login/login';
import { LayoutComponent } from './layout/layout/layout';
import { Dashboard } from './components/dashboard/dashboard';
import { authGuard, publicGuard } from './guards/auth.guard';
import { MenuManagementComponent } from './components/menu-management/menu-management';
import { PosTerminalComponent } from './components/pos/pos-terminal/pos-terminal';
import { TableManagementComponent } from './components/table-management/table-management';
import { OrdersBillingComponent } from './components/orders-billing/orders-billing';
import { CustomerQrOrderComponent } from './components/customer-qr-order/customer-qr-order';
import { KitchenDisplayComponent } from './components/kitchen-display/kitchen-display';
import { BusinessAnalyticsComponent } from './components/business-analytics/business-analytics';

export const routes: Routes = [
  { 
    path: '', 
    redirectTo: 'login', 
    pathMatch: 'full' 
  },
  { 
    path: 'login', 
    component: LoginComponent,
    canActivate: [publicGuard] // Prevents logged-in users from going back to login
  },
  // Public Customer QR Ordering Route (No staff login required)
  {
    path: 'order/:qrToken',
    component: CustomerQrOrderComponent
  },
  // Authenticated Staff Layout & Routes
  {
    path: '',
    component: LayoutComponent,
    canActivate: [authGuard], // Protects the layout and ALL its children
    children: [
      {
        path: 'settings',
        component: SystemSettingsComponent
      },
      {
        path: 'dashboard',
        component: Dashboard
      },
      {
        path: 'business-analytics',
        component: BusinessAnalyticsComponent
      },
      {
        path: 'pos-sale',
        component: PosTerminalComponent
      },
      {
        path: 'pos',
        redirectTo: 'pos-sale',
        pathMatch: 'full'
      },
      {
        path: 'kitchen',
        component: KitchenDisplayComponent
      },
      {
        path: 'tables',
        component: TableManagementComponent
      },
      {
        path: 'billing',
        component: OrdersBillingComponent
      },
      {
        path: 'menu-management',
        component: MenuManagementComponent
      }
    ]
  },
  { 
    path: '**', 
    redirectTo: 'login' 
  }
];