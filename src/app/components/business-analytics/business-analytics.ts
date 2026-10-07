import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AnalyticsService } from '../../services/analytics/analytics.service';
import { AlertService } from '../../services/alert.service';
import { BusinessInsightsResponse } from '../../models/analytics.models';

type DatePreset = 'TODAY' | 'WEEK' | 'MONTH' | 'CUSTOM';

@Component({
  selector: 'app-business-analytics',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './business-analytics.html',
  styleUrls: ['./business-analytics.css']
})
export class BusinessAnalyticsComponent implements OnInit {
  private analyticsService = inject(AnalyticsService);
  private alertService = inject(AlertService);

  insightsData = signal<BusinessInsightsResponse | null>(null);
  isLoading = signal(true);
  isExportingPdf = signal(false);

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
    this.initDefaultDates();
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

    this.analyticsService.getBusinessInsights(startIso, endIso).subscribe({
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

    this.analyticsService.downloadSalesReportPdf(startIso, endIso).subscribe({
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
