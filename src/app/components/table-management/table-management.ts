import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AlertService } from '../../services/alert.service';
import { TableService } from '../../services/tables/table.service';
import { TableResponse } from '../../models/pos/pos.models';

@Component({
  selector: 'app-table-management',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './table-management.html'
})
export class TableManagementComponent implements OnInit {
  private tableService = inject(TableService);
  private alertService = inject(AlertService);
  private fb = inject(FormBuilder);

  tables = signal<TableResponse[]>([]);
  isLoading = signal(false);
  isSubmitting = signal(false);

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
    tableNumber: ['', [Validators.required, Validators.maxLength(10)]]
  });

  ngOnInit() {
    this.loadTables();
  }

  loadTables() {
    this.isLoading.set(true);
    this.tableService.getTables().subscribe({
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

  addTable() {
    if (this.tableForm.invalid) return;
    
    this.isSubmitting.set(true);
    const tableNum = this.tableForm.getRawValue().tableNumber;

    this.tableService.createTable(tableNum).subscribe({
      next: () => {
        this.alertService.success(`Table ${tableNum} created successfully`);
        this.tableForm.reset();
        this.loadTables();
        this.isSubmitting.set(false);
      },
      error: () => {
        this.alertService.error('Failed to create table');
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