import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ToastrService } from 'ngx-toastr';
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
  private toastr = inject(ToastrService);
  private fb = inject(FormBuilder);

  tables = signal<TableResponse[]>([]);
  isLoading = signal(false);
  isSubmitting = signal(false);

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
        this.toastr.error('Failed to load tables');
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
        this.toastr.success(`Table ${tableNum} created successfully`);
        this.tableForm.reset();
        this.loadTables(); // Refresh list
        this.isSubmitting.set(false);
      },
      error: () => {
        this.toastr.error('Failed to create table');
        this.isSubmitting.set(false);
      }
    });
  }
}