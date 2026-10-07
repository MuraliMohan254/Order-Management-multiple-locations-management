import { Routes } from '@angular/router';
import { LoginComponent } from './components/login/login';
import { LayoutComponent } from './layout/layout/layout';
import { Dashboard } from './components/dashboard/dashboard';
import { authGuard, publicGuard } from './guards/auth.guard';
import { MenuManagementComponent } from './components/menu-management/menu-management';
import { PosTerminalComponent } from './components/pos/pos-terminal/pos-terminal';
import { TableManagementComponent } from './components/table-management/table-management';
import { OrdersBillingComponent } from './components/orders-billing/orders-billing';

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
  {
    path: '',
    component: LayoutComponent,
    canActivate: [authGuard], // Protects the layout and ALL its children
    children: [
      {
        path: 'dashboard',
        component: Dashboard
      },
       {
        path: 'menu-management',
        component: MenuManagementComponent
      },{
        path: 'pos-sale',
        component: PosTerminalComponent
      },
      { path: 'tables', component: TableManagementComponent },
      { path: 'billing', component: OrdersBillingComponent },
      // Future routes like 'pos', 'menu' go here
    ]
  },
  { 
    path: '**', 
    redirectTo: 'login' 
  }
];