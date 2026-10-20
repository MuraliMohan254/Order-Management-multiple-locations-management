import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule, ActivatedRoute, Router } from '@angular/router';
import { AlertService } from '../../services/alert.service';
import { AnalyticsService } from '../../services/analytics/analytics.service';
import { SalesDashboardResponse } from '../../models/analytics.models';
import { OutletService, Outlet } from '../../services/outlet/outlet';
import { CustomSelectComponent, SelectOption } from '../../shared/components/custom-select';

type DatePreset = 'TODAY' | 'WEEK' | 'MONTH' | 'CUSTOM';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, CustomSelectComponent],
  templateUrl: './dashboard.html',
  styleUrls: ['./dashboard.css'],
})
export class Dashboard implements OnInit {
  private analyticsService = inject(AnalyticsService);
  private alertService = inject(AlertService);
  private outletService = inject(OutletService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  outlets = signal<Outlet[]>([]);
  outletsLoading = signal<boolean>(false);
  selectedOutletId = signal<string | null>(null);

  outletOptions = computed<SelectOption[]>(() => {
    const list: SelectOption[] = [
      { value: null, label: 'Consolidated (All Outlets)', icon: '🌐' }
    ];
    this.outlets().forEach(o => {
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

  dashboardData = signal<SalesDashboardResponse | null>(null);
  isLoading = signal(true);
  isExportingDetailed = signal(false);
  isExportingSummary = signal(false);

  // Date Range Controls
  activePreset = signal<DatePreset>('MONTH');
  customStartDate = signal<string>('');
  customEndDate = signal<string>('');

  dineInPercentage = computed(() => {
    const data = this.dashboardData();
    if (!data || data.totalOrdersCount === 0) return 0;
    return Math.round((data.dineInOrdersCount / data.totalOrdersCount) * 100);
  });

  takeAwayPercentage = computed(() => {
    const data = this.dashboardData();
    if (!data || data.totalOrdersCount === 0) return 0;
    return 100 - this.dineInPercentage();
  });

  ngOnInit() {
    this.searchOutlets('');
    this.initDefaultDates();

    this.route.queryParams.subscribe(params => {
      const qOutlet = params['outletId'];
      if (qOutlet && qOutlet !== 'all' && qOutlet !== 'null') {
        this.selectedOutletId.set(qOutlet);
      } else if (qOutlet === 'all' || qOutlet === 'null') {
        this.selectedOutletId.set(null);
      }
      this.loadMetrics();
    });
  }

  searchOutlets(query: string = '') {
    this.outletsLoading.set(true);
    this.outletService.getActiveOutlets(query, 50).subscribe({
      next: (data) => {
        this.outlets.set(data || []);
        this.outletsLoading.set(false);
      },
      error: () => this.outletsLoading.set(false)
    });
  }

  onOutletChange(newOutletId?: string | null) {
    if (newOutletId !== undefined) {
      this.selectedOutletId.set(newOutletId);
    }
    const currentId = this.selectedOutletId();
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { outletId: currentId || null },
      queryParamsHandling: 'merge'
    });
    this.loadMetrics();
  }

  initDefaultDates() {
    const today = new Date();
    const thirtyDaysAgo = new Date();
    thirtyDaysAgo.setDate(today.getDate() - 30);

    this.customStartDate.set(thirtyDaysAgo.toISOString().split('T')[0]);
    this.customEndDate.set(today.toISOString().split('T')[0]);
  }

  setPreset(preset: DatePreset) {
    this.activePreset.set(preset);
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
      this.customStartDate.set(start.toISOString().split('T')[0]);
      this.customEndDate.set(today.toISOString().split('T')[0]);
      this.loadMetrics();
    }
  }

  applyCustomRange() {
    if (!this.customStartDate() || !this.customEndDate()) {
      this.alertService.warning('Please select both start and end dates.');
      return;
    }
    this.activePreset.set('CUSTOM');
    this.loadMetrics();
  }

  loadMetrics() {
    this.isLoading.set(true);

    const startIso = this.customStartDate() ? `${this.customStartDate()}T00:00:00Z` : undefined;
    const endIso = this.customEndDate() ? `${this.customEndDate()}T23:59:59Z` : undefined;

    this.analyticsService.getDashboardMetrics(startIso, endIso, this.selectedOutletId() || undefined).subscribe({
      next: (res) => {
        this.dashboardData.set(res);
        this.isLoading.set(false);
      },
      error: (err: any) => {
        if (err && err.status !== 403) this.alertService.error('Failed to load dashboard statistics');
        this.isLoading.set(false);
      }
    });
  }

  exportPdfReport() {
    this.isExportingDetailed.set(true);

    const startIso = this.customStartDate() ? `${this.customStartDate()}T00:00:00Z` : undefined;
    const endIso = this.customEndDate() ? `${this.customEndDate()}T23:59:59Z` : undefined;

    this.analyticsService.downloadSalesReportPdf(startIso, endIso, this.selectedOutletId() || undefined).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `WildBar_Sales_Detailed_${this.customStartDate()}_to_${this.customEndDate()}.pdf`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        window.URL.revokeObjectURL(url);
        this.alertService.success('Detailed sales report downloaded!');
        this.isExportingDetailed.set(false);
      },
      error: (err: any) => {
        this.alertService.error('Failed to generate PDF report');
        this.isExportingDetailed.set(false);
      }
    });
  }

  exportSummaryPdfReport() {
    this.isExportingSummary.set(true);

    const startIso = this.customStartDate() ? `${this.customStartDate()}T00:00:00Z` : undefined;
    const endIso = this.customEndDate() ? `${this.customEndDate()}T23:59:59Z` : undefined;

    this.analyticsService.downloadSalesSummaryPdf(startIso, endIso, this.selectedOutletId() || undefined).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `WildBar_Sales_Summary_${this.customStartDate()}_to_${this.customEndDate()}.pdf`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        window.URL.revokeObjectURL(url);
        this.alertService.success('Sales summary report downloaded!');
        this.isExportingSummary.set(false);
      },
      error: (err: any) => {
        this.alertService.error('Failed to generate summary PDF report');
        this.isExportingSummary.set(false);
      }
    });
  }
}


