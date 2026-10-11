import { Component, OnInit, OnDestroy, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, FormsModule, Validators } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { Subject, Subscription } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { OutletService, Outlet, OutletMonitoringItem, MultiOutletMonitoringResponse } from '../../services/outlet/outlet';
import { AnalyticsService } from '../../services/analytics/analytics.service';
import { PosService } from '../../services/pos/pos.service';
import { AlertService } from '../../services/alert.service';
import { InfiniteScrollDirective } from '../../shared/directives/infinite-scroll.directive';
import { CustomSelectComponent, SelectOption } from '../../shared/components/custom-select';

type ReportPreset = 'TODAY' | 'WEEK' | 'MONTH' | 'CUSTOM';
type ReportType = 'DAILY_BREAKDOWN' | 'MONTHLY_CONSOLIDATED' | 'DETAILED_TRANSACTIONS' | 'SALES_SUMMARY';
type ReportFormat = 'PDF' | 'XLSX';

@Component({
  selector: 'app-outlet-management',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule, RouterModule, InfiniteScrollDirective, CustomSelectComponent],
  templateUrl: './outlet-management.html',
})
export class OutletManagement implements OnInit, OnDestroy {
  private outletService = inject(OutletService);
  private analyticsService = inject(AnalyticsService);
  private posService = inject(PosService);
  private alertService = inject(AlertService);
  private router = inject(Router);
  private fb = inject(FormBuilder);

  // Global Multi-Outlet Telemetry Ribbon State
  monitoringData = signal<MultiOutletMonitoringResponse | null>(null);
  restaurantName = signal<string>('WildBar');
  isLoading = signal(true);
  isLoadingMore = signal(false);

  // Backend Search & Status Filter
  searchQuery = signal<string>('');
  statusFilter = signal<'ALL' | 'ACTIVE' | 'INACTIVE'>('ALL');
  viewMode = signal<'GRID' | 'TABLE'>('GRID');
  private searchSubject = new Subject<string>();
  private searchSubscription?: Subscription;

  // Pagination & Infinite Scroll State
  outlets = signal<OutletMonitoringItem[]>([]);
  allOutletsForDropdown = signal<OutletMonitoringItem[]>([]);
  currentPage = signal<number>(0);
  pageSize = signal<number>(12);
  totalElements = signal<number>(0);
  totalPages = signal<number>(1);
  isLastPage = signal<boolean>(false);

  // Outlet CRUD Modal State
  isModalOpen = signal(false);
  isEditing = signal(false);
  currentOutletId: string | null = null;
  outletForm: FormGroup;

  // Executive Reports Suite Modal State
  isReportModalOpen = signal(false);
  reportDatePreset = signal<ReportPreset>('MONTH');
  reportStartDate = signal<string>('');
  reportEndDate = signal<string>('');
  reportSelectedOutletId = signal<string | null>(null);
  reportType = signal<ReportType>('DAILY_BREAKDOWN');
  reportFormat = signal<ReportFormat>('PDF');
  isGeneratingReport = signal(false);

  reportOutletOptions = computed<SelectOption[]>(() => {
    const list: SelectOption[] = [
      { value: null, label: 'Consolidated (All Outlets)', icon: '🌐' }
    ];
    const items = this.allOutletsForDropdown().length > 0
      ? this.allOutletsForDropdown()
      : (this.monitoringData()?.outlets || []);
    items.forEach(o => {
      list.push({
        value: o.id,
        label: o.name,
        sublabel: o.code ? `Code: ${o.code}` : undefined,
        badge: o.active ? 'Active' : 'Inactive',
        icon: '📍'
      });
    });
    return list;
  });

  // Track active 1-click downloads
  downloadingState = signal<Record<string, boolean>>({});

  // Dynamic Report Title Preview
  reportTitlePreview = computed(() => {
    const brand = this.restaurantName();
    switch (this.reportType()) {
      case 'DAILY_BREAKDOWN':
        return `${brand} - Daily Multi-Outlet Sales Breakdown Report`;
      case 'MONTHLY_CONSOLIDATED':
        return `${brand} - Monthly Multi-Outlet Consolidated Performance Report`;
      case 'DETAILED_TRANSACTIONS':
        return `${brand} - Detailed Orders Transaction Audit Log`;
      case 'SALES_SUMMARY':
        return `${brand} - Consolidated Sales Volume & Revenue Summary`;
    }
  });

  // Dynamic Report Purpose & Description
  reportPurposeDescription = computed(() => {
    switch (this.reportType()) {
      case 'DAILY_BREAKDOWN':
        return 'Comprehensive day-by-day revenue breakdown, transaction counts, and settlement tracking aggregated across outlets with daily subtotals and grand totals.';
      case 'MONTHLY_CONSOLIDATED':
        return 'High-level executive monthly performance breakdown by outlet, capturing month-over-month sales velocity, average order values (AOV), and multi-outlet consolidation.';
      case 'DETAILED_TRANSACTIONS':
        return 'Granular order-by-order audit log featuring outlet name, table/type, timestamp, payment method, bill status, and settlement totals.';
      case 'SALES_SUMMARY':
        return 'Condensed day-wise revenue and order volume matrix designed for executive financial reconciliations.';
    }
  });

  constructor() {
    this.outletForm = this.fb.group({
      name: ['', Validators.required],
      code: ['', Validators.required],
      address: ['', Validators.required],
      contactNumber: ['', Validators.required],
      active: [true]
    });
  }

  ngOnInit() {
    this.initReportDates();
    this.loadRestaurantConfig();
    this.loadAllOutletsForDropdown();

    // Debounced Backend Search (350ms)
    this.searchSubscription = this.searchSubject.pipe(
      debounceTime(350),
      distinctUntilChanged()
    ).subscribe(query => {
      this.searchQuery.set(query);
      this.resetAndLoadOutlets();
    });

    // Initial Paginated Fetch
    this.resetAndLoadOutlets();
  }

  ngOnDestroy() {
    this.searchSubscription?.unsubscribe();
  }

  private loadRestaurantConfig() {
    this.posService.getAppConfig().subscribe({
      next: (cfg) => {
        if (cfg && cfg.restaurantName) {
          this.restaurantName.set(cfg.restaurantName);
        }
      },
      error: () => {}
    });
  }

  private loadAllOutletsForDropdown() {
    this.outletService.getActiveOutlets().subscribe({
      next: (list) => {
        this.allOutletsForDropdown.set(list.map(o => ({
          id: o.id || '',
          name: o.name,
          code: o.code,
          address: o.address,
          contactNumber: o.contactNumber,
          active: o.active,
          todaySales: 0,
          todayOrders: 0,
          activeOrders: 0,
          totalTables: 0,
          occupiedTables: 0,
          averageOrderValue: 0
        })));
      },
      error: () => {}
    });
  }

  initReportDates() {
    const today = new Date();
    const thirtyDaysAgo = new Date();
    thirtyDaysAgo.setDate(today.getDate() - 30);
    this.reportStartDate.set(thirtyDaysAgo.toISOString().split('T')[0]);
    this.reportEndDate.set(today.toISOString().split('T')[0]);
  }

  onSearchInput(value: string) {
    this.searchSubject.next(value);
  }

  clearSearch() {
    this.searchQuery.set('');
    this.searchSubject.next('');
  }

  setStatusFilter(status: 'ALL' | 'ACTIVE' | 'INACTIVE') {
    if (this.statusFilter() === status) return;
    this.statusFilter.set(status);
    this.resetAndLoadOutlets();
  }

  resetAndLoadOutlets() {
    this.currentPage.set(0);
    this.isLastPage.set(false);
    this.loadOutletsPage(true);
  }

  loadOutletsPage(reset: boolean) {
    if (reset) {
      this.isLoading.set(true);
    } else {
      this.isLoadingMore.set(true);
    }

    const page = this.currentPage();
    const size = this.pageSize();
    const search = this.searchQuery().trim();
    const status = this.statusFilter();

    this.outletService.getMonitoringSummary(page, size, search, status).subscribe({
      next: (res) => {
        this.monitoringData.set(res);
        this.totalElements.set(res.totalElements || 0);
        this.totalPages.set(res.totalPages || 1);
        this.isLastPage.set(res.last ?? true);

        if (reset) {
          this.outlets.set(res.outlets || []);
          this.isLoading.set(false);
        } else {
          this.outlets.update(prev => [...prev, ...(res.outlets || [])]);
          this.isLoadingMore.set(false);
        }
      },
      error: () => {
        this.alertService.error('Failed to load multi-outlet monitoring telemetry');
        this.isLoading.set(false);
        this.isLoadingMore.set(false);
      }
    });
  }

  onScroll() {
    if (this.isLoading() || this.isLoadingMore() || this.isLastPage()) {
      return;
    }
    this.currentPage.update(p => p + 1);
    this.loadOutletsPage(false);
  }

  // Navigation to Insights
  navigateToDashboard(outletId?: string) {
    if (outletId) {
      this.router.navigate(['/dashboard'], { queryParams: { outletId } });
    } else {
      this.router.navigate(['/dashboard']);
    }
  }

  navigateToBusinessAnalytics(outletId?: string) {
    if (outletId) {
      this.router.navigate(['/business-analytics'], { queryParams: { outletId } });
    } else {
      this.router.navigate(['/business-analytics']);
    }
  }

  // ========================================================
  // EXECUTIVE REPORTS SUITE MODAL
  // ========================================================
  openReportModal(targetOutletId?: string, defaultType?: ReportType) {
    if (targetOutletId) {
      this.reportSelectedOutletId.set(targetOutletId);
    } else {
      this.reportSelectedOutletId.set(null);
    }
    if (defaultType) {
      this.reportType.set(defaultType);
    }
    this.isReportModalOpen.set(true);
  }

  closeReportModal() {
    this.isReportModalOpen.set(false);
  }

  setReportPreset(preset: ReportPreset) {
    this.reportDatePreset.set(preset);
    const today = new Date();
    let start = new Date();

    if (preset === 'TODAY') {
      start.setHours(0, 0, 0, 0);
    } else if (preset === 'WEEK') {
      start.setDate(today.getDate() - 7);
    } else if (preset === 'MONTH') {
      start.setDate(today.getDate() - 30);
    }

    if (preset !== 'CUSTOM') {
      this.reportStartDate.set(start.toISOString().split('T')[0]);
      this.reportEndDate.set(today.toISOString().split('T')[0]);
    }
  }

  generateAndDownloadReport() {
    if (!this.reportStartDate() || !this.reportEndDate()) {
      this.alertService.warning('Please select both start and end dates.');
      return;
    }

    this.isGeneratingReport.set(true);
    const startIso = `${this.reportStartDate()}T00:00:00Z`;
    const endIso = `${this.reportEndDate()}T23:59:59Z`;
    const outletId = this.reportSelectedOutletId() || undefined;
    const type = this.reportType();
    const isXlsx = this.reportFormat() === 'XLSX';
    const brand = this.restaurantName().replace(/[^a-zA-Z0-9]/g, '_');

    let call$: any;
    let filenamePrefix = `${brand}_Report`;

    if (type === 'DAILY_BREAKDOWN') {
      call$ = isXlsx
        ? this.analyticsService.downloadDailyOutletReportXlsx(startIso, endIso, outletId)
        : this.analyticsService.downloadDailyOutletReportPdf(startIso, endIso, outletId);
      filenamePrefix = `${brand}_Multi_Outlet_Daily_Report`;
    } else if (type === 'MONTHLY_CONSOLIDATED') {
      call$ = isXlsx
        ? this.analyticsService.downloadMonthlyOutletReportXlsx(startIso, endIso, outletId)
        : this.analyticsService.downloadMonthlyOutletReportPdf(startIso, endIso, outletId);
      filenamePrefix = `${brand}_Multi_Outlet_Monthly_Report`;
    } else if (type === 'DETAILED_TRANSACTIONS') {
      call$ = isXlsx
        ? this.analyticsService.downloadSalesReportXlsx(startIso, endIso, outletId)
        : this.analyticsService.downloadSalesReportPdf(startIso, endIso, outletId);
      filenamePrefix = `${brand}_Detailed_Transactions_Report`;
    } else {
      call$ = isXlsx
        ? this.analyticsService.downloadSalesSummaryXlsx(startIso, endIso, outletId)
        : this.analyticsService.downloadSalesSummaryPdf(startIso, endIso, outletId);
      filenamePrefix = `${brand}_Sales_Summary_Report`;
    }

    const extension = isXlsx ? 'xlsx' : 'pdf';

    call$.subscribe({
      next: (blob: Blob) => {
        this.triggerBlobDownload(blob, `${filenamePrefix}_${this.reportStartDate()}_to_${this.reportEndDate()}.${extension}`);
        this.alertService.success(`${isXlsx ? 'Excel spreadsheet' : 'Jasper PDF'} report generated successfully!`);
        this.isGeneratingReport.set(false);
        this.closeReportModal();
      },
      error: () => {
        this.alertService.error(`Failed to generate ${isXlsx ? 'Excel' : 'PDF'} report`);
        this.isGeneratingReport.set(false);
      }
    });
  }

  // Quick 1-Click Multi-Outlet Daily Report
  downloadQuickDailyReport(format: ReportFormat = 'PDF') {
    const key = `quick-daily-${format}`;
    this.setDownloading(key, true);
    const brand = this.restaurantName().replace(/[^a-zA-Z0-9]/g, '_');
    const isXlsx = format === 'XLSX';

    const call$ = isXlsx
      ? this.analyticsService.downloadDailyOutletReportXlsx()
      : this.analyticsService.downloadDailyOutletReportPdf();

    call$.subscribe({
      next: (blob: Blob) => {
        const ext = isXlsx ? 'xlsx' : 'pdf';
        this.triggerBlobDownload(blob, `${brand}_Multi_Outlet_Daily_Report_${this.getTodayDateStr()}.${ext}`);
        this.alertService.success(`Daily multi-outlet breakdown report (${format}) downloaded!`);
        this.setDownloading(key, false);
      },
      error: () => {
        this.alertService.error('Failed to download daily multi-outlet report');
        this.setDownloading(key, false);
      }
    });
  }

  // Quick 1-Click Multi-Outlet Monthly Report
  downloadQuickMonthlyReport(format: ReportFormat = 'PDF') {
    const key = `quick-monthly-${format}`;
    this.setDownloading(key, true);
    const brand = this.restaurantName().replace(/[^a-zA-Z0-9]/g, '_');
    const isXlsx = format === 'XLSX';

    const call$ = isXlsx
      ? this.analyticsService.downloadMonthlyOutletReportXlsx()
      : this.analyticsService.downloadMonthlyOutletReportPdf();

    call$.subscribe({
      next: (blob: Blob) => {
        const ext = isXlsx ? 'xlsx' : 'pdf';
        this.triggerBlobDownload(blob, `${brand}_Multi_Outlet_Monthly_Report_${this.getTodayDateStr()}.${ext}`);
        this.alertService.success(`Monthly multi-outlet consolidated report (${format}) downloaded!`);
        this.setDownloading(key, false);
      },
      error: () => {
        this.alertService.error('Failed to download monthly multi-outlet report');
        this.setDownloading(key, false);
      }
    });
  }

  // Outlet-Specific Report Download
  downloadOutletReport(outlet: OutletMonitoringItem, type: 'daily' | 'monthly' | 'detailed', format: ReportFormat = 'PDF') {
    const key = `${outlet.id}-${type}-${format}`;
    this.setDownloading(key, true);
    const brand = this.restaurantName().replace(/[^a-zA-Z0-9]/g, '_');
    const safeName = outlet.name.replace(/[^a-zA-Z0-9]/g, '_');
    const isXlsx = format === 'XLSX';

    let call$: any;
    let label = 'Detailed';
    if (type === 'daily') {
      call$ = isXlsx
        ? this.analyticsService.downloadDailyOutletReportXlsx(undefined, undefined, outlet.id)
        : this.analyticsService.downloadDailyOutletReportPdf(undefined, undefined, outlet.id);
      label = 'Daily';
    } else if (type === 'monthly') {
      call$ = isXlsx
        ? this.analyticsService.downloadMonthlyOutletReportXlsx(undefined, undefined, outlet.id)
        : this.analyticsService.downloadMonthlyOutletReportPdf(undefined, undefined, outlet.id);
      label = 'Monthly';
    } else {
      call$ = isXlsx
        ? this.analyticsService.downloadSalesReportXlsx(undefined, undefined, outlet.id)
        : this.analyticsService.downloadSalesReportPdf(undefined, undefined, outlet.id);
      label = 'Detailed';
    }

    call$.subscribe({
      next: (blob: Blob) => {
        const ext = isXlsx ? 'xlsx' : 'pdf';
        this.triggerBlobDownload(blob, `${brand}_${safeName}_${label}_Report_${this.getTodayDateStr()}.${ext}`);
        this.alertService.success(`${outlet.name} ${label} report (${format}) downloaded!`);
        this.setDownloading(key, false);
      },
      error: () => {
        this.alertService.error(`Failed to download report for ${outlet.name}`);
        this.setDownloading(key, false);
      }
    });
  }

  private triggerBlobDownload(blob: Blob, filename: string) {
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    window.URL.revokeObjectURL(url);
  }

  private getTodayDateStr(): string {
    return new Date().toISOString().split('T')[0];
  }

  isDownloading(key: string): boolean {
    return Object.entries(this.downloadingState()).some(([k, v]) => !!v && (k === key || k.startsWith(key)));
  }

  private setDownloading(key: string, state: boolean) {
    this.downloadingState.update(map => ({ ...map, [key]: state }));
  }

  // ========================================================
  // OUTLET CRUD MODAL
  // ========================================================
  openCreateModal() {
    this.isEditing.set(false);
    this.currentOutletId = null;
    this.outletForm.reset({ active: true });
    this.isModalOpen.set(true);
  }

  openEditModal(outlet: OutletMonitoringItem | Outlet) {
    this.isEditing.set(true);
    this.currentOutletId = outlet.id!;
    this.outletForm.patchValue({
      name: outlet.name,
      code: outlet.code,
      address: outlet.address,
      contactNumber: outlet.contactNumber,
      active: outlet.active
    });
    this.isModalOpen.set(true);
  }

  closeModal() {
    this.isModalOpen.set(false);
  }

  saveOutlet() {
    if (this.outletForm.invalid) {
      this.alertService.warning('Please complete all required fields (Name, Code, Address, Contact Number).');
      return;
    }
    const outletData: Outlet = this.outletForm.value;

    if (this.isEditing() && this.currentOutletId) {
      this.outletService.updateOutlet(this.currentOutletId, outletData).subscribe({
        next: () => {
          this.alertService.success('Outlet profile updated successfully');
          this.resetAndLoadOutlets();
          this.loadAllOutletsForDropdown();
          this.closeModal();
        },
        error: (err: any) => {
          this.alertService.error(err.error?.message || 'Error updating outlet profile');
        }
      });
    } else {
      this.outletService.createOutlet(outletData).subscribe({
        next: () => {
          this.alertService.success('New outlet registered successfully');
          this.resetAndLoadOutlets();
          this.loadAllOutletsForDropdown();
          this.closeModal();
        },
        error: (err: any) => {
          this.alertService.error(err.error?.message || 'Error registering new outlet');
        }
      });
    }
  }

  async deleteOutlet(id: string, name: string) {
    const confirmed = await this.alertService.confirm({
      title: 'Delete Outlet?',
      text: `Are you sure you want to permanently delete outlet "${name}"? This action cannot be undone.`,
      confirmButtonText: 'Yes, delete outlet',
      cancelButtonText: 'Cancel',
      isDestructive: true
    });

    if (confirmed) {
      this.outletService.deleteOutlet(id).subscribe({
        next: () => {
          this.alertService.success(`Outlet "${name}" deleted successfully`);
          this.resetAndLoadOutlets();
          this.loadAllOutletsForDropdown();
        },
        error: (err: any) => {
          this.alertService.error(err.error?.message || 'Error deleting outlet');
        }
      });
    }
  }

  async toggleOutletStatus(outlet: OutletMonitoringItem) {
    const nextStatus = !outlet.active;
    const action = nextStatus ? 'Activate' : 'Deactivate';
    const confirmed = await this.alertService.confirm({
      title: `${action} Outlet?`,
      text: `Are you sure you want to ${action.toLowerCase()} outlet "${outlet.name}"?`,
      confirmButtonText: `Yes, ${action.toLowerCase()}`,
      cancelButtonText: 'Cancel',
      isDestructive: !nextStatus
    });

    if (confirmed) {
      this.outletService.updateOutlet(outlet.id, {
        name: outlet.name,
        code: outlet.code,
        address: outlet.address,
        contactNumber: outlet.contactNumber,
        active: nextStatus
      }).subscribe({
        next: () => {
          this.alertService.success(`Outlet "${outlet.name}" ${nextStatus ? 'activated' : 'deactivated'} successfully`);
          this.resetAndLoadOutlets();
          this.loadAllOutletsForDropdown();
        },
        error: (err: any) => {
          this.alertService.error(err.error?.message || `Failed to ${action.toLowerCase()} outlet`);
        }
      });
    }
  }
}
