import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule, ActivatedRoute, Router } from '@angular/router';
import { AnalyticsService } from '../../services/analytics/analytics.service';
import { AlertService } from '../../services/alert.service';
import { BusinessInsightsResponse } from '../../models/analytics.models';
import { AuthService } from '../../services/auth.service';
import { OutletService, Outlet } from '../../services/outlet/outlet';
import { CustomSelectComponent, SelectOption } from '../../shared/components/custom-select';

type DatePreset = 'TODAY' | 'WEEK' | 'MONTH' | 'CUSTOM';

@Component({
  selector: 'app-business-analytics',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, CustomSelectComponent],
  templateUrl: './business-analytics.html',
  styleUrls: ['./business-analytics.css']
})
export class BusinessAnalyticsComponent implements OnInit {
  private analyticsService = inject(AnalyticsService);
  private alertService = inject(AlertService);

  insightsData = signal<BusinessInsightsResponse | null>(null);
  isLoading = signal(true);
  private authService = inject(AuthService);
  private outletService = inject(OutletService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  
  isAdmin = signal(false);
  outlets = signal<Outlet[]>([]);
  outletsLoading = signal<boolean>(false);
  selectedOutletId = signal<string | null>(null);
  isExportingPdf = signal(false);

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

  // Date Range Controls
  activePreset = signal<DatePreset>('MONTH');
  customStartDate = signal<string>('');
  customEndDate = signal<string>('');

  // Computed metrics
  dineInRevShare = computed(() => {
    const d = this.insightsData();
    if (!d || d.netSales === 0) return 0;
    return Math.round((d.dineInRevenue / d.netSales) * 100);
  });

  takeAwayRevShare = computed(() => {
    const d = this.insightsData();
    if (!d || d.netSales === 0) return 0;
    return 100 - this.dineInRevShare();
  });

  peakHour = computed(() => {
    const d = this.insightsData();
    if (!d || !d.hourlyTraffic || d.hourlyTraffic.length === 0) return null;
    return [...d.hourlyTraffic].sort((a, b) => b.orderCount - a.orderCount)[0];
  });

  peakRevenueHour = computed(() => {
    const d = this.insightsData();
    if (!d || !d.hourlyTraffic || d.hourlyTraffic.length === 0) return null;
    return [...d.hourlyTraffic].sort((a, b) => b.revenue - a.revenue)[0];
  });

  maxHourlyOrders = computed(() => {
    const d = this.insightsData();
    if (!d || !d.hourlyTraffic || d.hourlyTraffic.length === 0) return 1;
    return Math.max(...d.hourlyTraffic.map(h => h.orderCount), 1);
  });

  ngOnInit() {
    this.isAdmin.set(this.authService.getUserRole() === 'ADMIN');
    if (this.isAdmin()) {
      this.searchOutlets('');
    }
    this.initDefaultDates();

    this.route.queryParams.subscribe(params => {
      const qOutlet = params['outletId'];
      if (qOutlet && qOutlet !== 'all' && qOutlet !== 'null') {
        this.selectedOutletId.set(qOutlet);
      } else if (qOutlet === 'all' || qOutlet === 'null') {
        this.selectedOutletId.set(null);
      }
      this.loadInsights();
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
    this.loadInsights();
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
    const start = new Date();

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
      this.loadInsights();
    }
  }

  applyCustomRange() {
    if (!this.customStartDate() || !this.customEndDate()) {
      this.alertService.warning('Please select both start and end dates.');
      return;
    }
    this.activePreset.set('CUSTOM');
    this.loadInsights();
  }

  loadInsights() {
    this.isLoading.set(true);

    const startIso = this.customStartDate() ? `${this.customStartDate()}T00:00:00Z` : undefined;
    const endIso = this.customEndDate() ? `${this.customEndDate()}T23:59:59Z` : undefined;

    this.analyticsService.getBusinessInsights(startIso, endIso, this.selectedOutletId() || undefined).subscribe({
      next: (res) => {
        this.insightsData.set(res);
        this.isLoading.set(false);
      },
      error: () => {
        this.alertService.error('Failed to load business insights');
        this.isLoading.set(false);
      }
    });
  }

  exportPdfReport() {
    this.isExportingPdf.set(true);

    const startIso = this.customStartDate() ? `${this.customStartDate()}T00:00:00Z` : undefined;
    const endIso = this.customEndDate() ? `${this.customEndDate()}T23:59:59Z` : undefined;

    this.analyticsService.downloadSalesReportPdf(startIso, endIso, this.selectedOutletId() || undefined).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `WildBar_Executive_Report_${this.customStartDate()}_to_${this.customEndDate()}.pdf`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        window.URL.revokeObjectURL(url);
        this.alertService.success('Executive PDF report generated and downloaded!');
        this.isExportingPdf.set(false);
      },
      error: () => {
        this.alertService.error('Failed to generate PDF report');
        this.isExportingPdf.set(false);
      }
    });
  }
}




