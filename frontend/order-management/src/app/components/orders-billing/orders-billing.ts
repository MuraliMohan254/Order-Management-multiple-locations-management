import { Component, OnInit, OnDestroy, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, Subscription } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { AlertService } from '../../services/alert.service';
import { BillingService } from '../../services/billing/billing.service';
import { PosService } from '../../services/pos/pos.service';
import { AuthService } from '../../services/auth.service';
import { OutletService, Outlet } from '../../services/outlet/outlet';
import { OrderResponse } from '../../models/pos/pos.models';
import { InfiniteScrollDirective } from '../../shared/directives/infinite-scroll.directive';
import { CustomSelectComponent, SelectOption } from '../../shared/components/custom-select';

@Component({
  selector: 'app-orders-billing',
  standalone: true,
  imports: [CommonModule, FormsModule, InfiniteScrollDirective, CustomSelectComponent],
  templateUrl: './orders-billing.html'
})
export class OrdersBillingComponent implements OnInit, OnDestroy {
  private billingService = inject(BillingService);
  private posService = inject(PosService);
  private alertService = inject(AlertService);
  private authService = inject(AuthService);
  private outletService = inject(OutletService);

  // Admin Outlet Filtering State
  isAdmin = computed(() => this.authService.isAdmin());
  outlets = signal<Outlet[]>([]);
  outletsLoading = signal<boolean>(false);
  selectedOutletFilter = signal<string>('all');

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

  // Backend Paginated Orders List
  orders = signal<OrderResponse[]>([]);
  selectedOrder = signal<OrderResponse | null>(null);
  
  // UI Filter & Search State
  currentFilter = signal<'ACTIVE' | 'BILLED' | 'CANCELLED'>('ACTIVE');
  searchQuery = signal<string>('');
  private searchSubject = new Subject<string>();
  private searchSubscription?: Subscription;

  // Pagination & Infinite Scroll State
  currentPage = signal<number>(0);
  pageSize = signal<number>(20);
  totalElements = signal<number>(0);
  isLastPage = signal<boolean>(false);
  isLoading = signal<boolean>(false);
  isLoadingMore = signal<boolean>(false);

  // Order Clubbing / Merging State
  showMergeModal = signal<boolean>(false);
  orderToMergeWith = signal<OrderResponse | null>(null);
  isMerging = signal<boolean>(false);

  mergeableOrders = computed(() => {
    const current = this.selectedOrder();
    if (!current) return [];
    return this.orders().filter(o => 
      o.id !== current.id && o.status !== 'BILLED' && o.status !== 'CANCELLED'
    );
  });

  isOrderBilledOrPaid = computed(() => {
    const o = this.selectedOrder();
    return o?.status === 'BILLED' || o?.status === 'PAID' || o?.status === 'CANCELLED';
  });

  calculatedTotalDue = computed(() => {
    const o = this.selectedOrder();
    if (!o) return 0;
    if (this.isOrderBilledOrPaid()) {
      return o.grandTotal ?? 0;
    }
    const subTotal = o.subTotal ?? 0;
    const taxAmount = o.taxAmount ?? 0;
    const disc = Number(this.discountInput() ?? 0);
    return Math.max(0, subTotal + taxAmount - disc);
  });
  
  isProcessing = signal(false);
  isPrintingDirect = signal<boolean>(false);
  
  discountInput = signal<number>(0);
  paymentMethod = signal<'CASH' | 'CARD' | 'UPI'>('CASH');
  showReceiptModal = signal(false);

  taxRate = signal<number>(0.05);
  taxName = signal<string>('GST');
  defaultDiscountPercentage = signal<number>(0);
  restaurantName = signal<string>('WildBar');
  gstNumber = signal<string>('');
  thermalPrintingEnabled = signal<boolean>(false);
  thermalPaperSize = signal<string>('80mm');

  trimOrderNumber(orderNumber?: string | null): string {
    if (!orderNumber || !orderNumber.trim()) return '';
    const trimmed = orderNumber.trim();
    const parts = trimmed.split('-');
    if (parts.length >= 3) {
      const prefix = parts[0];
      const suffix = parts[parts.length - 1];
      const hasLongTimestamp = parts.some(p => /^\d{8,}$/.test(p));
      if (hasLongTimestamp) {
        return `${prefix}-${suffix}`;
      }
    }
    if (/^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-/.test(trimmed)) {
      return trimmed.substring(0, 8).toUpperCase();
    }
    return trimmed;
  }

  ngOnInit() {
    this.posService.getAppConfig().subscribe({
      next: (cfg) => {
        if (cfg) {
          if (cfg.restaurantName) this.restaurantName.set(cfg.restaurantName);
          if (cfg.gstNumber) this.gstNumber.set(cfg.gstNumber);
          if (cfg.thermalPrintingEnabled !== undefined) this.thermalPrintingEnabled.set(cfg.thermalPrintingEnabled);
          if (cfg.thermalPaperSize) this.thermalPaperSize.set(cfg.thermalPaperSize);
          if (cfg.taxRate !== undefined) this.taxRate.set(Number(cfg.taxRate));
          if (cfg.taxName) this.taxName.set(cfg.taxName);
          if (cfg.defaultDiscountPercentage !== undefined && cfg.defaultDiscountPercentage > 0) {
            this.defaultDiscountPercentage.set(Number(cfg.defaultDiscountPercentage));
          }
        }
      },
      error: () => {}
    });

    if (this.isAdmin()) {
      this.searchOutlets('');
    }

    // Debounced Backend Search (350ms)
    this.searchSubscription = this.searchSubject.pipe(
      debounceTime(350),
      distinctUntilChanged()
    ).subscribe(query => {
      this.searchQuery.set(query);
      this.resetAndLoadOrders();
    });

    // Initial backend fetch
    this.resetAndLoadOrders();
  }

  onOutletFilterChange(outletId: string) {
    this.selectedOutletFilter.set(outletId);
    this.selectedOrder.set(null);
    this.resetAndLoadOrders();
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

  ngOnDestroy() {
    this.searchSubscription?.unsubscribe();
  }

  onSearchInput(value: string) {
    this.searchSubject.next(value);
  }

  clearSearch() {
    this.searchQuery.set('');
    this.searchSubject.next('');
  }

  setFilter(filter: 'ACTIVE' | 'BILLED' | 'CANCELLED') {
    this.currentFilter.set(filter);
    this.selectedOrder.set(null);
    this.resetAndLoadOrders();
  }

  private getQueryStatuses(): string[] {
    if (this.currentFilter() === 'ACTIVE') {
      return ['SOFT_ORDER', 'CONFIRMED', 'PREPARING', 'READY', 'SERVED'];
    } else if (this.currentFilter() === 'BILLED') {
      return ['BILLED', 'PAID'];
    } else if (this.currentFilter() === 'CANCELLED') {
      return ['CANCELLED'];
    }
    return [];
  }

  resetAndLoadOrders() {
    this.currentPage.set(0);
    this.isLastPage.set(false);
    this.loadOrdersPage(0, false);
  }

  loadOrdersPage(page: number, append: boolean = false) {
    if (append) {
      this.isLoadingMore.set(true);
    } else {
      this.isLoading.set(true);
    }

    const statuses = this.getQueryStatuses();
    const search = this.searchQuery();
    const outletId = this.isAdmin() && this.selectedOutletFilter() !== 'all' ? this.selectedOutletFilter() : undefined;

    this.billingService.getPagedOrders({
      statuses,
      search,
      outletId,
      page,
      size: this.pageSize(),
      sortBy: 'createdAt',
      sortDirection: 'DESC'
    }).subscribe({
      next: (res) => {
        if (append) {
          this.orders.update(prev => [...prev, ...res.content]);
          this.isLoadingMore.set(false);
        } else {
          this.orders.set(res.content);
          this.isLoading.set(false);
          // Auto-select first order if none selected or if selected order not in new results
          if (res.content.length > 0 && !this.selectedOrder()) {
            this.selectOrder(res.content[0].id);
          } else if (res.content.length === 0) {
            this.selectedOrder.set(null);
          }
        }

        this.currentPage.set(res.pageNumber);
        this.totalElements.set(res.totalElements);
        this.isLastPage.set(res.last);
      },
      error: () => {
        this.isLoading.set(false);
        this.isLoadingMore.set(false);
        this.alertService.error('Failed to load orders from server');
      }
    });
  }

  // Triggered when user reaches near the bottom of the orders container
  loadMoreOrders() {
    if (this.isLoading() || this.isLoadingMore() || this.isLastPage()) return;
    const nextPage = this.currentPage() + 1;
    this.loadOrdersPage(nextPage, true);
  }

  selectOrder(orderId: string) {
    this.billingService.getOrderById(orderId).subscribe({
      next: (order) => {
        this.selectedOrder.set(order);
        this.discountInput.set(order.discountAmount !== undefined && order.discountAmount !== null ? Number(order.discountAmount) : 0);
      },
      error: () => this.alertService.error('Failed to fetch order details')
    });
  }
  
  
  generateAndSettleBill() {
    const order = this.selectedOrder();
    if (!order) return;
    
    this.isProcessing.set(true);
    this.billingService.generateBill(order.id, this.discountInput()).subscribe({
      next: (billedOrder) => {
        this.alertService.success(`Bill generated for ${billedOrder.orderNumber}`);
        const amountToPay = billedOrder.grandTotal > 0 ? billedOrder.grandTotal : billedOrder.subTotal;
        this.billingService.settlePayment(billedOrder.id, this.paymentMethod(), amountToPay).subscribe({
          next: () => {
            this.alertService.success(`Payment of ₹${amountToPay.toFixed(2)} settled via ${this.paymentMethod()}!`);
            const cashier = this.authService.getUsername();
            billedOrder.billedBy = billedOrder.billedBy || cashier;
            billedOrder.settledBy = billedOrder.settledBy || cashier;
            billedOrder.status = 'PAID';
            this.selectedOrder.set(billedOrder);
            this.discountInput.set(billedOrder.discountAmount !== undefined && billedOrder.discountAmount !== null ? Number(billedOrder.discountAmount) : 0);
            this.showReceiptModal.set(true);
            this.resetAndLoadOrders();
            this.isProcessing.set(false);
          },
          error: () => {
            this.alertService.error('Bill generated, but payment failed. Please try settling again.');
            this.selectedOrder.set(billedOrder);
            this.discountInput.set(billedOrder.discountAmount !== undefined && billedOrder.discountAmount !== null ? Number(billedOrder.discountAmount) : 0);
            this.resetAndLoadOrders();
            this.isProcessing.set(false);
          }
        });
      },
      error: () => {
        this.alertService.error('Failed to generate bill');
        this.isProcessing.set(false);
      }
    });
  }

  generateBill() {
    const order = this.selectedOrder();
    if (!order) return;
    
    this.isProcessing.set(true);
    this.billingService.generateBill(order.id, this.discountInput()).subscribe({
      next: (updatedOrder) => {
        this.alertService.success(`Bill generated for ${updatedOrder.orderNumber}`);
        const cashier = this.authService.getUsername();
        updatedOrder.billedBy = updatedOrder.billedBy || cashier;
        this.selectedOrder.set(updatedOrder);
        this.discountInput.set(updatedOrder.discountAmount !== undefined && updatedOrder.discountAmount !== null ? Number(updatedOrder.discountAmount) : 0);
        this.showReceiptModal.set(true);
        this.resetAndLoadOrders(); 
        this.isProcessing.set(false);
      },
      error: () => {
        this.alertService.error('Failed to generate bill');
        this.isProcessing.set(false);
      }
    });
  }

  openMergeModal() {
    this.orderToMergeWith.set(null);
    this.showMergeModal.set(true);
  }

  confirmMerge() {
    const target = this.selectedOrder();
    const source = this.orderToMergeWith();
    if (!target || !source) return;

    this.isMerging.set(true);
    this.billingService.mergeOrders(target.id, source.id).subscribe({
      next: (merged) => {
        this.alertService.success(`Successfully clubbed Order #${source.orderNumber} into #${target.orderNumber}!`, 'Orders Clubbed');
        this.selectedOrder.set(merged);
        this.discountInput.set(merged.discountAmount !== undefined && merged.discountAmount !== null ? Number(merged.discountAmount) : 0);
        this.showMergeModal.set(false);
        this.isMerging.set(false);
        this.resetAndLoadOrders();
      },
      error: (err) => {
        this.alertService.error(err?.error?.error || 'Failed to club orders');
        this.isMerging.set(false);
      }
    });
  }

  settlePayment() {
    const order = this.selectedOrder();
    if (!order) return;

    this.isProcessing.set(true);

    const doSettle = (targetOrder: OrderResponse) => {
      const amountToPay = targetOrder.grandTotal > 0 ? targetOrder.grandTotal : targetOrder.subTotal;
      this.billingService.settlePayment(targetOrder.id, this.paymentMethod(), amountToPay).subscribe({
        next: () => {
          this.alertService.success(`Payment of ₹${amountToPay.toFixed(2)} settled via ${this.paymentMethod()}!`);
          const cashier = this.authService.getUsername();
          targetOrder.billedBy = targetOrder.billedBy || cashier;
          targetOrder.settledBy = targetOrder.settledBy || cashier;
          targetOrder.status = 'PAID';
          this.selectedOrder.set(targetOrder);
          this.discountInput.set(targetOrder.discountAmount !== undefined && targetOrder.discountAmount !== null ? Number(targetOrder.discountAmount) : 0);
          this.showReceiptModal.set(true);
          this.resetAndLoadOrders();
          this.isProcessing.set(false);
        },
        error: () => {
          this.alertService.error('Payment failed');
          this.isProcessing.set(false);
        }
      });
    };

    if (order.status !== 'BILLED' && order.status !== 'PAID') {
      const newDiscount = Number(this.discountInput() || 0);
      const currentDiscount = Number(order.discountAmount || 0);

      if (newDiscount !== currentDiscount) {
        this.billingService.generateBill(order.id, newDiscount).subscribe({
          next: (updatedOrder) => {
            doSettle(updatedOrder);
          },
          error: () => {
            this.alertService.error('Failed to update discount on bill');
            this.isProcessing.set(false);
          }
        });
        return;
      }
    }

    doSettle(order);
  }

  async voidItem(orderItemId: string) {
    const order = this.selectedOrder();
    if (!order) return;

    if (order.status === 'SERVED' || order.kitchenStatus === 'SERVED' || order.status === 'BILLED' || order.status === 'PAID' || order.status === 'CANCELLED') {
      this.alertService.warning('Cannot void items once an order is served, billed, or paid.');
      return;
    }

    const confirmed = await this.alertService.confirm({
      title: 'Void Item?',
      text: 'Are you sure you want to remove this item from the order?',
      confirmButtonText: 'Yes, void item',
      isDestructive: true
    });
    if (!confirmed) return;

    this.posService.voidOrderItem(order.id, orderItemId).subscribe({
      next: (updated) => {
        this.alertService.success('Item voided from order');
        this.selectedOrder.set(updated);
        this.resetAndLoadOrders();
      },
      error: () => this.alertService.error('Failed to void item')
    });
  }

  async cancelEntireOrder() {
    const order = this.selectedOrder();
    if (!order) return;

    if (order.status === 'SERVED' || order.kitchenStatus === 'SERVED' || order.status === 'BILLED' || order.status === 'PAID' || order.status === 'CANCELLED') {
      this.alertService.warning('Cannot cancel an order that is served, billed, or paid.');
      return;
    }

    const confirmed = await this.alertService.confirm({
      title: `Cancel Order ${order.orderNumber}?`,
      text: 'This will cancel the entire order and free the assigned table.',
      confirmButtonText: 'Yes, cancel order',
      isDestructive: true
    });
    if (!confirmed) return;

    this.posService.cancelOrder(order.id).subscribe({
      next: (updated) => {
        this.alertService.warning(`Order ${order.orderNumber} cancelled`);
        this.selectedOrder.set(null);
        this.resetAndLoadOrders();
      },
      error: () => this.alertService.error('Failed to cancel order')
    });
  }

  printReceipt() {
    const order = this.selectedOrder();
    if (!order) {
      window.print();
      return;
    }

    this.isPrintingDirect.set(true);
    this.posService.printThermalBill(order.id).subscribe({
      next: (res) => {
        this.isPrintingDirect.set(false);
        if (res.success) {
          this.alertService.success('⚡ Customer Bill printed to thermal printer!', 'Thermal Print Success');
          this.showReceiptModal.set(false);
        } else {
          this.alertService.warning(res.message + ' - Opening browser print preview...');
          window.print();
        }
      },
      error: () => {
        this.isPrintingDirect.set(false);
        window.print();
      }
    });
  }

  acceptOrder() {
    const order = this.selectedOrder();
    if (!order) return;

    this.posService.updateOrderStatus(order.id, 'CONFIRMED').subscribe({
      next: (updated) => {
        this.alertService.success(`Order ${order.orderNumber} accepted and sent to kitchen`);
        this.selectedOrder.set(updated);
        this.resetAndLoadOrders();
      },
      error: () => this.alertService.error('Failed to accept order')
    });
  }
}

