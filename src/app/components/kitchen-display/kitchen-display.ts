import { Component, OnInit, OnDestroy, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AlertService } from '../../services/alert.service';
import { PosService } from '../../services/pos/pos.service';
import { AuthService } from '../../services/auth.service';
import { OutletService, Outlet } from '../../services/outlet/outlet';
import { OrderResponse } from '../../models/pos/pos.models';
import { CustomSelectComponent, SelectOption } from '../../shared/components/custom-select';

@Component({
  selector: 'app-kitchen-display',
  standalone: true,
  imports: [CommonModule, FormsModule, CustomSelectComponent],
  templateUrl: './kitchen-display.html',
  styleUrls: ['./kitchen-display.css']
})
export class KitchenDisplayComponent implements OnInit, OnDestroy {
  private posService = inject(PosService);
  private alertService = inject(AlertService);
  private authService = inject(AuthService);
  private outletService = inject(OutletService);

  isAdmin = computed(() => this.authService.isAdmin());
  outlets = signal<Outlet[]>([]);
  outletsLoading = signal<boolean>(false);
  selectedOutletId = signal<string>('all');

  outletOptions = computed<SelectOption[]>(() => {
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

  orders = signal<OrderResponse[]>([]);
  isLoading = signal(true);
  isAutoRefresh = signal(true);
  soundEnabled = signal(true);
  filterStatus = signal<'ALL' | 'CONFIRMED' | 'PREPARING' | 'READY'>('ALL');
  filterType = signal<'ALL' | 'DINE_IN' | 'TAKE_AWAY'>('ALL');

  currentTime = signal(new Date());
  updatingOrderId = signal<string | null>(null);
  private pollingIntervalId: any = null;
  private clockIntervalId: any = null;
  private previousOrderCount = 0;

  getKitchenStage(order: OrderResponse): string {
    if (order.kitchenStatus) return order.kitchenStatus;
    if (order.status && order.status !== 'BILLED' && order.status !== 'PAID') return order.status;
    return 'CONFIRMED';
  }

  filteredOrders = computed(() => {
    let list = this.orders().filter(o => {
      const stage = this.getKitchenStage(o);
      return stage !== 'SERVED' && stage !== 'CANCELLED' && o.status !== 'CANCELLED';
    });

    const status = this.filterStatus();
    const type = this.filterType();

    if (status !== 'ALL') {
      list = list.filter(o => this.getKitchenStage(o) === status);
    }

    if (type !== 'ALL') {
      list = list.filter(o => o.orderType === type);
    }

    // Sort by createdAt ascending (oldest order first to cook first - FIFO)
    return [...list].sort((a, b) => {
      const timeA = a.createdAt ? new Date(a.createdAt).getTime() : 0;
      const timeB = b.createdAt ? new Date(b.createdAt).getTime() : 0;
      return timeA - timeB;
    });
  });

  pendingCount = computed(() => this.orders().filter(o => this.getKitchenStage(o) === 'CONFIRMED').length);
  preparingCount = computed(() => this.orders().filter(o => this.getKitchenStage(o) === 'PREPARING').length);
  readyCount = computed(() => this.orders().filter(o => this.getKitchenStage(o) === 'READY').length);

  volume = signal(0.8);

  ngOnInit() {
    if (this.isAdmin()) {
      this.searchOutlets('');
    }
    this.loadActiveOrders(true);
    this.startClock();
    this.startPolling();
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

  onOutletChange(newOutletId: string) {
    this.selectedOutletId.set(newOutletId);
    this.loadActiveOrders(true);
  }

  toggleSound() {
    this.soundEnabled.set(!this.soundEnabled());
  }

  ngOnDestroy() {
    if (this.pollingIntervalId) clearInterval(this.pollingIntervalId);
    if (this.clockIntervalId) clearInterval(this.clockIntervalId);
  }

  startClock() {
    this.clockIntervalId = setInterval(() => {
      this.currentTime.set(new Date());
    }, 1000);
  }

  startPolling() {
    this.pollingIntervalId = setInterval(() => {
      if (this.isAutoRefresh()) {
        this.loadActiveOrders(false);
      }
    }, 5000);
  }

  loadActiveOrders(isInitial = false) {
    if (isInitial) this.isLoading.set(true);

    // Active kitchen statuses: CONFIRMED, PREPARING, READY, plus BILLED and PAID (for pre-paid/express orders)
    const statuses = ['CONFIRMED', 'PREPARING', 'READY', 'BILLED', 'PAID'];
    const outletId = this.isAdmin() && this.selectedOutletId() !== 'all' ? this.selectedOutletId() : undefined;
    this.posService.getOrders(statuses, outletId).subscribe({
      next: (data) => {
        // Exclude orders where kitchen preparation is already SERVED or order is CANCELLED
        const activeKitchenOrders = data.filter(o => {
          const stage = this.getKitchenStage(o);
          return stage !== 'SERVED' && stage !== 'CANCELLED' && o.status !== 'CANCELLED';
        });

        // Check for new incoming orders to play chime sound
        if (activeKitchenOrders.length > this.previousOrderCount && !isInitial && this.soundEnabled()) {
          this.playKitchenChime();
          this.alertService.info('New incoming order received!', 'Kitchen Alert');
        }
        this.previousOrderCount = activeKitchenOrders.length;
        this.orders.set(activeKitchenOrders);
        this.isLoading.set(false);
      },
      error: () => {
        if (isInitial) this.alertService.error('Failed to load kitchen orders');
        this.isLoading.set(false);
      }
    });
  }

  clearAll() {
    this.alertService.confirm({
      title: 'Clear Kitchen Display?',
      text: 'This will instantly mark all active tickets as Served. Are you sure?',
      confirmButtonText: 'Yes, clear all',
      isDestructive: true
    }).then(confirmed => {
      if (confirmed) {
        this.isLoading.set(true);
        this.posService.clearKitchen().subscribe({
          next: () => {
            this.alertService.success('Kitchen queue cleared successfully.');
            this.loadActiveOrders(false);
          },
          error: (err) => {
            const errMsg = err?.error?.error || 'Failed to clear queue';
            this.alertService.error(errMsg);
            this.isLoading.set(false);
          }
        });
      }
    });
  }

  updateStatus(orderId: string, newStatus: string) {
    if (this.updatingOrderId()) return; // Prevent spamming while in-flight
    this.updatingOrderId.set(orderId);

    // Optimistically update status in UI immediately for snappy response
    const previousOrders = this.orders();
    this.orders.update(orders =>
      orders.map(o => {
        if (o.id === orderId) {
          return {
            ...o,
            kitchenStatus: newStatus,
            status: o.status === 'BILLED' ? 'BILLED' : newStatus
          };
        }
        return o;
      }).filter(o => {
        // If order is marked as SERVED, it should leave active kitchen screen
        return newStatus !== 'SERVED' || o.id !== orderId;
      })
    );

    this.posService.updateOrderStatus(orderId, newStatus).subscribe({
      next: (updated) => {
        this.updatingOrderId.set(null);
        this.alertService.success(`Order ${updated.orderNumber} updated to ${newStatus}`);
        this.loadActiveOrders(false);
      },
      error: (err) => {
        // Revert on error
        this.orders.set(previousOrders);
        this.updatingOrderId.set(null);
        const errMsg = err?.error?.error || err?.message || 'Server error';
        this.alertService.error(`Failed to update status to ${newStatus}: ${errMsg}`);
      }
    });
  }

  getElapsedMinutes(createdAt?: string): number {
    if (!createdAt) return 0;
    const diffMs = this.currentTime().getTime() - new Date(createdAt).getTime();
    return Math.floor(diffMs / 60000);
  }

  getElapsedClass(minutes: number): string {
    if (minutes > 20) return 'text-rose-600 dark:text-rose-400 bg-rose-50 dark:bg-rose-950/60 border-rose-200 dark:border-rose-800 animate-pulse';
    if (minutes > 10) return 'text-amber-600 dark:text-amber-400 bg-amber-50 dark:bg-amber-950/60 border-amber-200 dark:border-amber-800';
    return 'text-slate-600 dark:text-slate-300 bg-slate-100 dark:bg-slate-700/60 border-slate-200 dark:border-slate-600';
  }

  playKitchenChime() {
    if (!this.soundEnabled()) return;
    try {
      const audioCtx = new (window.AudioContext || (window as any).webkitAudioContext)();
      const osc = audioCtx.createOscillator();
      const gain = audioCtx.createGain();
      
      // More urgent "Ding-Dong" or loud pulse
      osc.type = 'triangle';
      osc.frequency.setValueAtTime(600, audioCtx.currentTime); 
      osc.frequency.exponentialRampToValueAtTime(800, audioCtx.currentTime + 0.1);
      
      gain.gain.setValueAtTime(0, audioCtx.currentTime);
      gain.gain.linearRampToValueAtTime(this.volume(), audioCtx.currentTime + 0.05); // Use dynamic volume
      gain.gain.exponentialRampToValueAtTime(0.01, audioCtx.currentTime + 0.5);
      
      osc.connect(gain);
      gain.connect(audioCtx.destination);
      osc.start();
      osc.stop(audioCtx.currentTime + 0.5);
      
      // Optional: Speech Synthesis for "New Order"
      if ('speechSynthesis' in window) {
         const utterance = new SpeechSynthesisUtterance('New Order');
         utterance.volume = this.volume();
         utterance.rate = 1.2;
         window.speechSynthesis.speak(utterance);
      }

    } catch {
      // AudioContext unavailable or blocked
    }
  }
}
