import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { AlertService } from '../../services/alert.service';
import { OutletService } from '../../services/outlet/outlet';
import { CustomSelectComponent, SelectOption } from '../../shared/components/custom-select';

@Component({
  selector: 'app-user-management',
  standalone: true,
  imports: [CommonModule, FormsModule, CustomSelectComponent],
  template: `
    <div class="p-4 sm:p-6 h-full flex flex-col bg-slate-50 dark:bg-slate-900 overflow-y-auto">
      <div class="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 mb-6">
        <div>
          <h1 class="text-2xl font-black text-slate-900 dark:text-white">User Management</h1>
          <p class="text-sm text-slate-500">Manage staff access, roles, and outlet assignments</p>
        </div>
        <button (click)="openCreateModal()" class="w-full sm:w-auto px-5 py-2.5 bg-blue-600 hover:bg-blue-700 text-white rounded-xl font-bold shadow-lg shadow-blue-500/30 transition-all flex items-center justify-center gap-2 cursor-pointer">
          <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M18 9v3m0 0v3m0-3h3m-3 0h-3m-2-5a4 4 0 11-8 0 4 4 0 018 0zM3 20a6 6 0 0112 0v1H3v-1z"/>
          </svg>
          New User
        </button>
      </div>

      <div class="bg-white dark:bg-slate-800 rounded-2xl shadow-xs border border-slate-200 dark:border-slate-700 overflow-hidden">
        <div class="overflow-x-auto">
          <table class="w-full text-left text-sm min-w-[650px]">
            <thead class="bg-slate-100 dark:bg-slate-900/50 text-slate-500 dark:text-slate-400 font-bold uppercase text-xs">
              <tr>
                <th class="px-6 py-4">Username</th>
                <th class="px-6 py-4">Role</th>
                <th class="px-6 py-4">Assigned Outlet</th>
                <th class="px-6 py-4">Status</th>
                <th class="px-6 py-4 text-right">Actions</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-200 dark:divide-slate-700">
              @for (user of users(); track user.id) {
                <tr class="hover:bg-slate-50 dark:hover:bg-slate-700/50 transition-colors">
                  <td class="px-6 py-4 font-bold text-slate-900 dark:text-white flex items-center gap-3">
                    <div class="w-8 h-8 rounded-full bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 flex items-center justify-center font-bold">
                      {{ user.username.charAt(0).toUpperCase() }}
                    </div>
                    <span>{{ user.username }}</span>
                  </td>
                  <td class="px-6 py-4">
                    <span class="px-3 py-1 rounded-full text-xs font-bold border"
                          [ngClass]="{
                            'bg-indigo-50 border-indigo-200 text-indigo-700 dark:bg-indigo-900/30 dark:border-indigo-800 dark:text-indigo-300': user.roles?.includes('ADMIN'),
                            'bg-emerald-50 border-emerald-200 text-emerald-700 dark:bg-emerald-900/30 dark:border-emerald-800 dark:text-emerald-300': user.roles?.includes('CASHIER'),
                            'bg-amber-50 border-amber-200 text-amber-700 dark:bg-amber-900/30 dark:border-amber-800 dark:text-amber-300': user.roles?.includes('KITCHEN')
                          }">
                      {{ user.roles?.join(', ') || 'USER' }}
                    </span>
                  </td>
                  <td class="px-6 py-4">
                    @if (user.outletName) {
                      <span class="px-3 py-1 rounded-full text-xs font-bold inline-flex items-center gap-1.5 border border-blue-200 dark:border-blue-800 bg-blue-50 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300">
                        <span>📍</span>
                        {{ user.outletName }}
                      </span>
                    } @else {
                      <span class="px-3 py-1 rounded-full text-xs font-medium inline-flex items-center gap-1.5 border border-slate-200 dark:border-slate-700 bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-400">
                        <span class="w-1.5 h-1.5 rounded-full bg-slate-400"></span>
                        Global (All Outlets)
                      </span>
                    }
                  </td>
                  <td class="px-6 py-4">
                    <span [class]="user.active ? 'text-emerald-600 dark:text-emerald-400 bg-emerald-100 dark:bg-emerald-900/30' : 'text-rose-600 dark:text-rose-400 bg-rose-100 dark:bg-rose-900/30'"
                          class="px-3 py-1 rounded-full text-xs font-bold inline-flex items-center gap-1.5 border"
                          [ngClass]="user.active ? 'border-emerald-200 dark:border-emerald-800' : 'border-rose-200 dark:border-rose-800'">
                      <span class="w-1.5 h-1.5 rounded-full" [ngClass]="user.active ? 'bg-emerald-500' : 'bg-rose-500'"></span>
                      {{ user.active ? 'Active' : 'Disabled' }}
                    </span>
                  </td>
                  <td class="px-6 py-4 text-right">
                    <div class="flex items-center justify-end gap-1">
                      <button (click)="openEditModal(user)" 
                              class="p-2 text-slate-400 hover:text-blue-600 hover:bg-blue-50 dark:hover:bg-blue-900/30 rounded-lg transition-colors cursor-pointer"
                              title="Edit User & Outlet">
                        <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"/>
                        </svg>
                      </button>
                      <button (click)="toggleStatus(user)" 
                              class="p-2 rounded-lg transition-colors cursor-pointer"
                              [ngClass]="user.active ? 'text-rose-500 hover:bg-rose-50 dark:hover:bg-rose-900/30' : 'text-emerald-500 hover:bg-emerald-50 dark:hover:bg-emerald-900/30'"
                              [title]="user.active ? 'Disable User' : 'Enable User'">
                        @if(user.active) {
                          <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M18.364 18.364A9 9 0 005.636 5.636m12.728 12.728A9 9 0 015.636 5.636m12.728 12.728L5.636 5.636"/>
                          </svg>
                        } @else {
                          <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"/>
                          </svg>
                        }
                      </button>
                      <button (click)="deleteUser(user)" 
                              class="p-2 text-slate-400 hover:text-rose-600 hover:bg-rose-50 dark:hover:bg-rose-900/30 rounded-lg transition-colors cursor-pointer"
                              title="Delete User">
                        <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/>
                        </svg>
                      </button>
                    </div>
                  </td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="5" class="px-6 py-8 text-center text-slate-400">
                    No users found.
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </div>

      <!-- Create Modal -->
      @if (showCreateModal()) {
        <div class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 backdrop-blur-sm p-4">
          <div class="bg-white dark:bg-slate-800 p-6 sm:p-8 rounded-2xl w-full max-w-md shadow-2xl border border-slate-200 dark:border-slate-700 animate-in fade-in zoom-in duration-200">
            <h2 class="text-xl sm:text-2xl font-black mb-6 text-slate-900 dark:text-white flex items-center gap-3">
              <div class="w-9 h-9 rounded-xl bg-blue-100 dark:bg-blue-900/40 text-blue-600 dark:text-blue-400 flex items-center justify-center">
                <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M18 9v3m0 0v3m0-3h3m-3 0h-3m-2-5a4 4 0 11-8 0 4 4 0 018 0zM3 20a6 6 0 0112 0v1H3v-1z"/>
                </svg>
              </div>
              Create User
            </h2>
            
            <div class="space-y-4">
              <div>
                <label class="block text-sm font-bold text-slate-700 dark:text-slate-300 mb-1.5">Username *</label>
                <input type="text" [(ngModel)]="newUser.username" placeholder="Enter username" class="w-full p-2.5 rounded-xl border border-slate-300 dark:border-slate-600 bg-slate-50 dark:bg-slate-900 dark:text-white text-xs font-bold focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none transition-all">
              </div>
              
              <div>
                <label class="block text-sm font-bold text-slate-700 dark:text-slate-300 mb-1.5">Password *</label>
                <input type="password" [(ngModel)]="newUser.password" placeholder="Min 5 characters" class="w-full p-2.5 rounded-xl border border-slate-300 dark:border-slate-600 bg-slate-50 dark:bg-slate-900 dark:text-white text-xs font-bold focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none transition-all">
                @if(newUser.password && newUser.password.length < 5) {
                  <p class="text-rose-500 text-xs mt-1 font-medium">Password must be at least 5 characters</p>
                }
              </div>
              
              <div>
                <label class="block text-sm font-bold text-slate-700 dark:text-slate-300 mb-1.5">Role *</label>
                <app-custom-select
                  [options]="roleOptions"
                  [selected]="newUser.role"
                  (selectedChange)="newUser.role = $event"
                  placeholder="Select role"
                  [searchable]="false"
                ></app-custom-select>
              </div>

              <div>
                <label class="block text-sm font-bold text-slate-700 dark:text-slate-300 mb-1.5">Outlet Assignment</label>
                <app-custom-select
                  [options]="outletOptions()"
                  [selected]="newUser.outletId"
                  (selectedChange)="newUser.outletId = $event"
                  [serverSearch]="true"
                  [loading]="outletsLoading()"
                  (searchChange)="searchOutlets($event)"
                  placeholder="Select assigned outlet"
                  searchPlaceholder="Search outlet by name or code..."
                  [allowClear]="true"
                ></app-custom-select>
              </div>
            </div>

            <div class="flex justify-end gap-3 mt-8">
              <button (click)="showCreateModal.set(false)" class="px-5 py-2.5 text-slate-600 dark:text-slate-300 bg-slate-100 dark:bg-slate-700 hover:bg-slate-200 dark:hover:bg-slate-600 rounded-xl font-bold text-xs transition-colors cursor-pointer">Cancel</button>
              <button (click)="createUser()" [disabled]="!newUser.username || !newUser.password || newUser.password.length < 5" class="px-5 py-2.5 text-white bg-blue-600 hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed rounded-xl font-bold text-xs shadow-lg shadow-blue-500/30 transition-all cursor-pointer">Create Account</button>
            </div>
          </div>
        </div>
      }

      <!-- Edit Modal -->
      @if (showEditModal()) {
        <div class="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 backdrop-blur-sm p-4">
          <div class="bg-white dark:bg-slate-800 p-6 sm:p-8 rounded-2xl w-full max-w-md shadow-2xl border border-slate-200 dark:border-slate-700 animate-in fade-in zoom-in duration-200">
            <h2 class="text-xl sm:text-2xl font-black mb-6 text-slate-900 dark:text-white flex items-center gap-3">
              <div class="w-9 h-9 rounded-xl bg-blue-100 dark:bg-blue-900/40 text-blue-600 dark:text-blue-400 flex items-center justify-center">
                <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"/>
                </svg>
              </div>
              Edit User & Outlet
            </h2>
            
            <div class="space-y-4">
              <div>
                <label class="block text-sm font-bold text-slate-700 dark:text-slate-300 mb-1.5">Username</label>
                <input type="text" [(ngModel)]="editUser.username" placeholder="Username" class="w-full p-2.5 rounded-xl border border-slate-300 dark:border-slate-600 bg-slate-50 dark:bg-slate-900 dark:text-white text-xs font-bold focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none transition-all">
              </div>
              
              <div>
                <label class="block text-sm font-bold text-slate-700 dark:text-slate-300 mb-1.5">Role *</label>
                <app-custom-select
                  [options]="roleOptions"
                  [selected]="editUser.role"
                  (selectedChange)="editUser.role = $event"
                  placeholder="Select role"
                  [searchable]="false"
                ></app-custom-select>
              </div>

              <div>
                <label class="block text-sm font-bold text-slate-700 dark:text-slate-300 mb-1.5">Outlet Assignment</label>
                <app-custom-select
                  [options]="outletOptions()"
                  [selected]="editUser.outletId"
                  (selectedChange)="editUser.outletId = $event"
                  [serverSearch]="true"
                  [loading]="outletsLoading()"
                  (searchChange)="searchOutlets($event)"
                  placeholder="Select assigned outlet"
                  searchPlaceholder="Search outlet by name or code..."
                  [allowClear]="true"
                ></app-custom-select>
              </div>
            </div>

            <div class="flex justify-end gap-3 mt-8">
              <button (click)="showEditModal.set(false)" class="px-5 py-2.5 text-slate-600 dark:text-slate-300 bg-slate-100 dark:bg-slate-700 hover:bg-slate-200 dark:hover:bg-slate-600 rounded-xl font-bold text-xs transition-colors cursor-pointer">Cancel</button>
              <button (click)="saveUserUpdate()" [disabled]="!editUser.username" class="px-5 py-2.5 text-white bg-blue-600 hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed rounded-xl font-bold text-xs shadow-lg shadow-blue-500/30 transition-all cursor-pointer">Save Changes</button>
            </div>
          </div>
        </div>
      }
    </div>
  `
})
export class UserManagementComponent implements OnInit {
  private http = inject(HttpClient);
  private alertService = inject(AlertService);
  private outletService = inject(OutletService);

  users = signal<any[]>([]);
  outlets = signal<any[]>([]);
  outletsLoading = signal<boolean>(false);
  showCreateModal = signal(false);
  showEditModal = signal(false);

  newUser = { username: '', password: '', role: 'CASHIER', outletId: null as string | null };
  editUser = { id: 0, username: '', role: 'CASHIER', outletId: null as string | null };

  roleOptions: SelectOption[] = [
    { value: 'ADMIN', label: 'ADMIN (Full Access)', icon: '👑', badge: 'Full Access' },
    { value: 'CASHIER', label: 'CASHIER (POS & Billing)', icon: '💳', badge: 'POS & Billing' },
    { value: 'KITCHEN', label: 'KITCHEN (Kitchen Display)', icon: '🍳', badge: 'KDS Only' }
  ];

  outletOptions = computed<SelectOption[]>(() => {
    const list: SelectOption[] = [
      { value: null, label: 'Global (All Outlets)', icon: '🌐', badge: 'All Outlets' }
    ];
    this.outlets().forEach(o => {
      list.push({
        value: o.id,
        label: o.name,
        sublabel: o.code ? `Code: ${o.code}` : undefined,
        badge: o.code || undefined,
        icon: '📍'
      });
    });
    return list;
  });

  ngOnInit() {
    this.loadUsers();
    this.searchOutlets('');
  }

  searchOutlets(query: string = '') {
    this.outletsLoading.set(true);
    this.outletService.getActiveOutlets(query, 50).subscribe({
      next: (res) => {
        this.outlets.set(res || []);
        this.outletsLoading.set(false);
      },
      error: () => this.outletsLoading.set(false)
    });
  }

  loadUsers() {
    this.http.get<any>(`${environment.apiUrl}/fetch/all-users`).subscribe({
      next: (res) => this.users.set(res.data || res.content || []),
      error: () => this.alertService.error('Failed to load users')
    });
  }

  openCreateModal() {
    this.newUser = { username: '', password: '', role: 'CASHIER', outletId: null };
    this.showCreateModal.set(true);
  }

  openEditModal(user: any) {
    const primaryRole = (user.roles && user.roles.length > 0) ? user.roles[0] : 'CASHIER';
    this.editUser = {
      id: user.id,
      username: user.username,
      role: primaryRole,
      outletId: user.outletId || null
    };
    this.showEditModal.set(true);
  }

  createUser() {
    if (!this.newUser.username || this.newUser.password.length < 5) {
      this.alertService.warning('Please enter a username and a password of at least 5 characters.');
      return;
    }

    const payload = {
      username: this.newUser.username,
      password: this.newUser.password,
      roles: [this.newUser.role],
      outletId: this.newUser.outletId
    };

    this.http.post(`${environment.apiUrl}/users`, payload).subscribe({
      next: () => {
        this.alertService.success('User created successfully');
        this.showCreateModal.set(false);
        this.loadUsers();
      },
      error: (err) => this.alertService.error(err.error?.message || 'Failed to create user')
    });
  }

  saveUserUpdate() {
    if (!this.editUser.username) {
      this.alertService.warning('Please enter a username.');
      return;
    }

    const payload = {
      username: this.editUser.username,
      roles: [this.editUser.role],
      outletId: this.editUser.outletId
    };

    this.http.put(`${environment.apiUrl}/users/${this.editUser.id}`, payload).subscribe({
      next: () => {
        this.alertService.success('User updated successfully');
        this.showEditModal.set(false);
        this.loadUsers();
      },
      error: (err) => this.alertService.error(err.error?.message || 'Failed to update user')
    });
  }

  async toggleStatus(user: any) {
    const action = user.active ? 'disable' : 'enable';
    const confirm = await this.alertService.confirm({
      title: `${action === 'disable' ? 'Disable' : 'Enable'} User?`,
      text: `Are you sure you want to ${action} ${user.username}? ${action === 'disable' ? 'They will no longer be able to log in.' : ''}`,
      isDestructive: action === 'disable',
      confirmButtonText: `Yes, ${action} user`
    });

    if (confirm) {
      this.http.patch(`${environment.apiUrl}/users/${user.id}/status?active=${!user.active}`, {}).subscribe({
        next: () => {
          this.alertService.success(`User ${!user.active ? 'enabled' : 'disabled'}`);
          this.loadUsers();
        },
        error: () => this.alertService.error('Failed to update status')
      });
    }
  }

  async deleteUser(user: any) {
    const confirm = await this.alertService.confirm({
      title: 'Delete User?',
      text: `Are you sure you want to permanently delete ${user.username}? This cannot be undone.`,
      isDestructive: true,
      confirmButtonText: 'Yes, delete user'
    });

    if (confirm) {
      this.http.delete(`${environment.apiUrl}/users/${user.id}`).subscribe({
        next: () => {
          this.alertService.success('User deleted successfully');
          this.loadUsers();
        },
        error: (err) => this.alertService.error(err.error?.message || 'Failed to delete user')
      });
    }
  }
}
