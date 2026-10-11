import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, FormsModule, Validators } from '@angular/forms';
import { AlertService } from '../../services/alert.service';
import { TableService } from '../../services/tables/table.service';
import { PosService } from '../../services/pos/pos.service';
import { AuthService } from '../../services/auth.service';
import { OutletService, Outlet } from '../../services/outlet/outlet';
import { TableResponse } from '../../models/pos/pos.models';
import { CustomSelectComponent, SelectOption } from '../../shared/components/custom-select';

@Component({
  selector: 'app-table-management',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule, CustomSelectComponent],
  templateUrl: './table-management.html'
})
export class TableManagementComponent implements OnInit {
  private tableService = inject(TableService);
  private posService = inject(PosService);
  private alertService = inject(AlertService);
  private authService = inject(AuthService);
  private outletService = inject(OutletService);
  private fb = inject(FormBuilder);

  isAdmin = computed(() => this.authService.isAdmin());
  outlets = signal<Outlet[]>([]);
  outletsLoading = signal<boolean>(false);
  selectedOutletFilter = signal<string>('all');

  filterOutletOptions = computed<SelectOption[]>(() => {
    const list: SelectOption[] = [
      { value: 'all', label: 'All Outlets (Consolidated)', icon: '🏢', badge: 'All' }
    ];
    this.outlets().forEach(o => {
      if (o.id) {
        list.push({
          value: o.id,
          label: o.name,
          sublabel: o.code ? `Code: ${o.code}` : undefined,
          badge: o.code || undefined,
          icon: '📍'
        });
      }
    });
    return list;
  });

  formOutletOptions = computed<SelectOption[]>(() => {
    return this.outlets().map(o => ({
      value: o.id!,
      label: o.name,
      sublabel: o.code ? `Code: ${o.code}` : undefined,
      badge: o.code || undefined,
      icon: '📍'
    }));
  });

  tables = signal<TableResponse[]>([]);
  isLoading = signal(false);
  isSubmitting = signal(false);
  restaurantName = signal<string>('WildBar');

  // QR Modal State
  selectedTableForQr = signal<TableResponse | null>(null);
  showQrModal = signal(false);

  // Filter
  statusFilter = signal<'ALL' | 'AVAILABLE' | 'OCCUPIED'>('ALL');

  filteredTables = computed(() => {
    const list = this.tables();
    const filter = this.statusFilter();
    if (filter === 'ALL') return list;
    return list.filter(t => t.status === filter);
  });

  tableForm = this.fb.nonNullable.group({
    tableNumber: ['', [Validators.required, Validators.maxLength(10)]],
    outletId: ['']
  });

  ngOnInit() {
    this.posService.getAppConfig().subscribe({
      next: (cfg) => {
        if (cfg && cfg.restaurantName) {
          this.restaurantName.set(cfg.restaurantName);
        }
      },
      error: () => {}
    });

    if (this.isAdmin()) {
      this.searchOutlets('');
    }

    this.loadTables();
  }

  loadTables() {
    this.isLoading.set(true);
    const outletId = this.isAdmin() && this.selectedOutletFilter() !== 'all' ? this.selectedOutletFilter() : undefined;
    this.tableService.getTables(outletId).subscribe({
      next: (data) => {
        this.tables.set(data);
        this.isLoading.set(false);
      },
      error: () => {
        this.alertService.error('Failed to load tables');
        this.isLoading.set(false);
      }
    });
  }

  onOutletFilterChange(outletId: string) {
    this.selectedOutletFilter.set(outletId);
    this.loadTables();
  }

  searchOutlets(query: string = '') {
    this.outletsLoading.set(true);
    this.outletService.getActiveOutlets(query, 50).subscribe({
      next: (list) => {
        this.outlets.set(list || []);
        this.outletsLoading.set(false);
        if (!this.tableForm.get('outletId')?.value && list && list.length > 0 && list[0].id) {
          this.tableForm.patchValue({ outletId: list[0].id });
        }
      },
      error: () => this.outletsLoading.set(false)
    });
  }

  addTable() {
    if (this.tableForm.invalid) return;
    
    this.isSubmitting.set(true);
    const raw = this.tableForm.getRawValue();
    const tableNum = raw.tableNumber;
    const outletId = this.isAdmin() && raw.outletId ? raw.outletId : undefined;

    this.tableService.createTable(tableNum, outletId).subscribe({
      next: () => {
        this.alertService.success(`Table ${tableNum} created successfully`);
        this.tableForm.patchValue({ tableNumber: '' });
        this.loadTables();
        this.isSubmitting.set(false);
      },
      error: (err) => {
        this.alertService.error(err?.error?.error || 'Failed to create table');
        this.isSubmitting.set(false);
      }
    });
  }

  openQrModal(table: TableResponse) {
    this.selectedTableForQr.set(table);
    this.showQrModal.set(true);
  }

  getTableOrderUrl(qrToken: string): string {
    return `${window.location.origin}/order/${qrToken}`;
  }

  getQrImageUrl(qrToken: string): string {
    const url = this.getTableOrderUrl(qrToken);
    return `https://api.qrserver.com/v1/create-qr-code/?size=300x300&margin=10&data=${encodeURIComponent(url)}`;
  }

  copyQrLink(qrToken: string) {
    const url = this.getTableOrderUrl(qrToken);
    navigator.clipboard.writeText(url).then(() => {
      this.alertService.success('QR ordering link copied to clipboard!');
    }).catch(() => {
      this.alertService.info(url, 'Order URL');
    });
  }

  printQrCard() {
    window.print();
  }
}