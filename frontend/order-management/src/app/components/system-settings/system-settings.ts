import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { PosService } from '../../services/pos/pos.service';
import { AlertService } from '../../services/alert.service';
import { SharedService } from '../../services/shared';
import { CustomSelectComponent, SelectOption } from '../../shared/components/custom-select';

@Component({
  selector: 'app-system-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, CustomSelectComponent],
  templateUrl: './system-settings.html',
  styleUrls: ['./system-settings.css']
})
export class SystemSettingsComponent implements OnInit {
  private posService = inject(PosService);
  private alertService = inject(AlertService);
  private sharedService = inject(SharedService);

  isLoading = signal(true);
  initialKitchenWorkflowEnabled: boolean = true;
  isSaving = signal(false);

  // Config State
  kitchenWorkflowEnabled = signal<boolean>(true);
  taxRate = signal<number>(0.05); // 5% = 0.05
  taxName = signal<string>('GST');
  defaultDiscountPercentage = signal<number>(0);
  thermalPrintingEnabled = signal<boolean>(false);
  thermalPaperSize = signal<string>('80mm');
  printerName = signal<string>(''); // 0%
  printerIp = signal<string>('192.168.31.53');
  printerPort = signal<number>(9100);
  isTestingPrinter = signal<boolean>(false);
  restaurantName = signal<string>('WildBar');
  gstNumber = signal<string>('');
  quickBillPrintReceipt = signal<boolean>(true);

  paperSizeOptions: SelectOption[] = [
    { value: '80mm', label: '80mm (Standard POS)', icon: '📄', badge: 'Standard' },
    { value: '58mm', label: '58mm (Compact POS)', icon: '🧾', badge: 'Compact' }
  ];

  testPrint() {
    this.isTestingPrinter.set(true);
    this.posService.printTestReceipt(this.printerIp(), this.printerPort()).subscribe({
      next: (res) => {
        this.isTestingPrinter.set(false);
        if (res.success) {
          this.alertService.success(`⚡ Test receipt printed successfully to ${this.printerIp()}:${this.printerPort()}!`);
        } else {
          this.alertService.error(`Could not reach printer at ${this.printerIp()}:${this.printerPort()}. Ensure printer is ON.`);
        }
      },
      error: (err) => {
        this.isTestingPrinter.set(false);
        this.alertService.error(`Error sending test print: ${err?.error?.message || err.message}`);
      }
    });
  }

  ngOnInit() {
    this.loadConfig();
  }

  loadConfig() {
    this.isLoading.set(true);
    this.posService.getAppConfig().subscribe({
      next: (cfg) => {
        if (cfg) {
          if (cfg.restaurantName) this.restaurantName.set(cfg.restaurantName);
          if (cfg.gstNumber !== undefined) this.gstNumber.set(cfg.gstNumber || '');
          if (cfg.kitchenWorkflowEnabled !== undefined) {
            this.kitchenWorkflowEnabled.set(cfg.kitchenWorkflowEnabled);
            this.initialKitchenWorkflowEnabled = cfg.kitchenWorkflowEnabled;
          }
          if (cfg.taxRate !== undefined) this.taxRate.set(Number(cfg.taxRate) * 100); // Display as 5, not 0.05
          if (cfg.taxName) this.taxName.set(cfg.taxName);
          if (cfg.defaultDiscountPercentage !== undefined) this.defaultDiscountPercentage.set(Number(cfg.defaultDiscountPercentage) * 100);
          if (cfg.thermalPrintingEnabled !== undefined) this.thermalPrintingEnabled.set(cfg.thermalPrintingEnabled);
          if (cfg.thermalPaperSize !== undefined) this.thermalPaperSize.set(cfg.thermalPaperSize);
          if (cfg.printerName !== undefined) this.printerName.set(cfg.printerName || '');
          if (cfg.quickBillPrintReceipt !== undefined) this.quickBillPrintReceipt.set(cfg.quickBillPrintReceipt);
          const localPrinter = this.posService.getLocalPrinterSettings();
          this.printerIp.set(localPrinter.ip);
          this.printerPort.set(localPrinter.port);
        }
        this.isLoading.set(false);
      },
      error: () => {
        this.alertService.error('Failed to load system configuration.');
        this.isLoading.set(false);
      }
    });
  }

  async saveConfig() {
    this.isSaving.set(true);
    
    if (this.initialKitchenWorkflowEnabled !== this.kitchenWorkflowEnabled()) {
      const shouldClear = await this.alertService.confirm({
        title: 'Clear Kitchen Queue?',
        text: `You have ${this.kitchenWorkflowEnabled() ? 'enabled' : 'disabled'} the kitchen workflow. Do you want to clear the existing kitchen queue and start fresh? If you cancel, the old queue will be retained.`,
        confirmButtonText: 'Yes, clear queue',
        cancelButtonText: 'No, retain queue',
        isDestructive: true
      });

      if (shouldClear) {
        try {
          await firstValueFrom(this.posService.clearKitchen());
          this.alertService.success('Kitchen queue cleared.');
        } catch (e) {
          this.alertService.error('Failed to clear kitchen queue');
        }
      }
    }

    const payload = {
      restaurantName: this.restaurantName() || 'WildBar',
      kitchenWorkflowEnabled: this.kitchenWorkflowEnabled(),
      taxRate: this.taxRate() / 100,
      taxName: this.taxName(),
      defaultDiscountPercentage: this.defaultDiscountPercentage() / 100,
      thermalPrintingEnabled: this.thermalPrintingEnabled(),
      thermalPaperSize: this.thermalPaperSize(),
      printerName: this.printerName() || null,
      printerIp: this.printerIp() || '192.168.31.53',
      printerPort: Number(this.printerPort()) || 9100,
      gstNumber: this.gstNumber() ? this.gstNumber().trim().toUpperCase() : null,
      quickBillPrintReceipt: this.quickBillPrintReceipt()
    };

    this.posService.updateAppConfig(payload).subscribe({
      next: () => {
        // Save terminal-specific printer IP and Port locally
        if (typeof localStorage !== 'undefined') {
          if (this.printerIp()) localStorage.setItem('LOCAL_PRINTER_IP', this.printerIp().trim());
          if (this.printerPort()) localStorage.setItem('LOCAL_PRINTER_PORT', String(this.printerPort()));
        }
        this.alertService.success('System configuration saved successfully.');
        if (this.restaurantName()) {
          this.sharedService.setAppName(this.restaurantName());
        }
        this.initialKitchenWorkflowEnabled = this.kitchenWorkflowEnabled();
        this.isSaving.set(false);
      },
      error: () => {
        this.alertService.error('Failed to save configuration.');
        this.isSaving.set(false);
      }
    });
  }
}
