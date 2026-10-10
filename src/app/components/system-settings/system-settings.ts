import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PosService } from '../../services/pos/pos.service';
import { AlertService } from '../../services/alert.service';

@Component({
  selector: 'app-system-settings',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './system-settings.html',
  styleUrls: ['./system-settings.css']
})
export class SystemSettingsComponent implements OnInit {
  private posService = inject(PosService);
  private alertService = inject(AlertService);

  isLoading = signal(true);
  isSaving = signal(false);

  // Config State
  kitchenWorkflowEnabled = signal<boolean>(true);
  taxRate = signal<number>(0.05); // 5% = 0.05
  taxName = signal<string>('GST');
  defaultDiscountPercentage = signal<number>(0);
  thermalPrintingEnabled = signal<boolean>(false);
  thermalPaperSize = signal<string>('80mm');
  printerName = signal<string>(''); // 0%

  ngOnInit() {
    this.loadConfig();
  }

  loadConfig() {
    this.isLoading.set(true);
    this.posService.getAppConfig().subscribe({
      next: (cfg) => {
        if (cfg) {
          if (cfg.kitchenWorkflowEnabled !== undefined) this.kitchenWorkflowEnabled.set(cfg.kitchenWorkflowEnabled);
          if (cfg.taxRate !== undefined) this.taxRate.set(Number(cfg.taxRate) * 100); // Display as 5, not 0.05
          if (cfg.taxName) this.taxName.set(cfg.taxName);
          if (cfg.defaultDiscountPercentage !== undefined) this.defaultDiscountPercentage.set(Number(cfg.defaultDiscountPercentage) * 100);
        }
        this.isLoading.set(false);
      },
      error: () => {
        this.alertService.error('Failed to load system configuration.');
        this.isLoading.set(false);
      }
    });
  }

  saveConfig() {
    this.isSaving.set(true);
    const payload = {
      kitchenWorkflowEnabled: this.kitchenWorkflowEnabled(),
      taxRate: this.taxRate() / 100, // Convert 5 back to 0.05
      taxName: this.taxName(),
      defaultDiscountPercentage: this.defaultDiscountPercentage() / 100
    };

    this.posService.updateAppConfig(payload).subscribe({
      next: () => {
        this.alertService.success('System configuration saved successfully.');
        this.isSaving.set(false);
      },
      error: () => {
        this.alertService.error('Failed to save configuration.');
        this.isSaving.set(false);
      }
    });
  }
}
