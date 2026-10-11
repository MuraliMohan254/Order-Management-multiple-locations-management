import { Component, OnInit, OnDestroy, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Subject, Subscription } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { AlertService } from '../../../services/alert.service';
import { PosService } from '../../../services/pos/pos.service';
import { MenuService } from '../../../services/menu.service';
import { BillingService } from '../../../services/billing/billing.service';
import { AuthService } from '../../../services/auth.service';
import { OutletService, Outlet } from '../../../services/outlet/outlet';
import { CategoryResponse, ItemResponse } from '../../../models/menu.models';
import { CartItem, OrderItemRequest, TableResponse, OrderResponse } from '../../../models/pos/pos.models';
import { InfiniteScrollDirective } from '../../../shared/directives/infinite-scroll.directive';
import { CustomSelectComponent, SelectOption } from '../../../shared/components/custom-select';

@Component({
  selector: 'app-pos-terminal',
  standalone: true,
  imports: [CommonModule, FormsModule, InfiniteScrollDirective, CustomSelectComponent],
  templateUrl: './pos-terminal.html'
})
export class PosTerminalComponent implements OnInit, OnDestroy {
  private posService = inject(PosService);
  private billingService = inject(BillingService);
  private menuService = inject(MenuService);
  private alertService = inject(AlertService);
  private authService = inject(AuthService);
  private outletService = inject(OutletService);
  private router = inject(Router);

  // Admin Outlet Selection State
  isAdmin = computed(() => this.authService.isAdmin());
  outlets = signal<Outlet[]>([]);
  outletsLoading = signal<boolean>(false);
  selectedOutletId = signal<string>('');

  outletOptions = computed<SelectOption[]>(() => {
    return this.outlets().map(o => ({
      value: o.id!,
      label: o.name,
      sublabel: o.code ? `Code: ${o.code}` : undefined,
      badge: o.code || undefined,
      icon: '📍'
    }));
  });

  // Menu State
  categories = signal<CategoryResponse[]>([]);
  activeCategory = signal<string | null>(null);
  menuItems = signal<ItemResponse[]>([]);
  searchQuery = signal<string>('');
  isLoadingMenu = signal(false);

  // Cart & Order State
  cart = signal<CartItem[]>([]);
  orderType = signal<'TAKE_AWAY' | 'DINE_IN'>('TAKE_AWAY');
  isProcessing = signal(false);
  selectedItemForNote = signal<CartItem | null>(null);
  noteInput = signal<string>('');

  // Mobile responsive view toggle
  mobileTab = signal<'MENU' | 'CART'>('MENU');

  // Table State
  tables = signal<TableResponse[]>([]);
  selectedTable = signal<TableResponse | null>(null);
  tableActiveOrder = signal<OrderResponse | null>(null);
  showTableModal = signal(false);
  isLoadingTables = signal(false);

  // Running Tabs State (Backend Paginated with Search and Infinite Scroll)
  runningOrders = signal<OrderResponse[]>([]);
  showRunningTabsModal = signal(false);
  showExistingItems = signal(false);
  isLoadingRunningOrders = signal(false);
  isLoadingMoreRunningTabs = signal(false);
  runningTabsPage = signal<number>(0);
  runningTabsPageSize = signal<number>(20);
  runningTabsTotal = signal<number>(0);
  runningTabsIsLast = signal<boolean>(false);
  runningTabsSearch = signal<string>('');
  runningTabsTypeFilter = signal<'ALL' | 'DINE_IN' | 'TAKE_AWAY'>('ALL');

  private runningTabsSearchSubject = new Subject<string>();
  private runningTabsSearchSub?: Subscription;
  private menuSearchSubject = new Subject<string>();
  private menuSearchSub?: Subscription;

  runningDineInCount = computed(() => this.runningOrders().filter(o => o.orderType === 'DINE_IN').length);
  runningTakeAwayCount = computed(() => this.runningOrders().filter(o => o.orderType === 'TAKE_AWAY').length);

  // Directly return menu items (searched or category-filtered from backend)
  filteredMenuItems = computed(() => this.menuItems());

  cartSubtotal = computed(() => {
    return this.cart().reduce((sum, item) => sum + (item.price * item.quantity), 0);
  });

  cartCount = computed(() => {
    return this.cart().reduce((sum, item) => sum + item.quantity, 0);
  });

  taxRate = signal<number>(0.05);
  taxName = signal<string>('GST');
  defaultDiscountPercentage = signal<number>(0);
  restaurantName = signal<string>('WildBar');
  gstNumber = signal<string>('');
  thermalPrintingEnabled = signal<boolean>(false);
  thermalPaperSize = signal<string>('80mm');
  showReceiptModal = signal<boolean>(false);
  receiptOrder = signal<OrderResponse | null>(null);

  printReceipt() {
    window.print();
  }

  // Cart Discount state
  cartDiscountPercent = signal<number | null>(null);
  cartCustomDiscountAmount = signal<number | null>(null);
  cartDiscountUserModified = signal<boolean>(false);
  showDiscountBox = signal<boolean>(false);

  cartTax = computed(() => {
    return this.cartSubtotal() * this.taxRate();
  });

  cartDiscountAmount = computed(() => {
    const gross = this.cartSubtotal() + this.cartTax();
    if (gross <= 0) return 0;

    // 1. If cashier entered a custom flat amount in ₹:
    const customAmt = this.cartCustomDiscountAmount();
    if (customAmt !== null) {
      return Math.min(Math.max(0, customAmt), gross);
    }

    // 2. If cashier explicitly selected a percentage pill:
    const percent = this.cartDiscountPercent();
    if (percent !== null) {
      if (percent <= 0) return 0;
      return Math.min(Math.round(gross * (percent / 100)), gross);
    }

    // 3. Fallback to system default global discount:
    if (!this.cartDiscountUserModified() && this.defaultDiscountPercentage() > 0) {
      return Math.min(Math.round(gross * this.defaultDiscountPercentage()), gross);
    }

    return 0;
  });

  cartDiscount = computed(() => {
    return this.cartDiscountAmount();
  });

  cartTotal = computed(() => {
    const gross = this.cartSubtotal() + this.cartTax();
    return Math.max(0, gross - this.cartDiscountAmount());
  });

  existingTabSubtotal = computed(() => {
    return this.tableActiveOrder()?.subTotal || 0;
  });

  existingTabDiscount = computed(() => {
    return this.tableActiveOrder()?.discountAmount || 0;
  });

  projectedCombinedSubtotal = computed(() => {
    return this.existingTabSubtotal() + this.cartSubtotal();
  });

  projectedCombinedTax = computed(() => {
    return this.projectedCombinedSubtotal() * this.taxRate();
  });

  projectedCombinedDiscount = computed(() => {
    const currentCartDisc = this.cartDiscountAmount();
    if (currentCartDisc > 0) {
      return currentCartDisc;
    }
    const tabDisc = this.existingTabDiscount();
    if (tabDisc > 0) {
      return tabDisc;
    }
    if (!this.cartDiscountUserModified() && this.defaultDiscountPercentage() > 0) {
      const gross = this.projectedCombinedSubtotal() + this.projectedCombinedTax();
      return Math.min(Math.round(gross * this.defaultDiscountPercentage()), gross);
    }
    return 0;
  });

  projectedCombinedTotal = computed(() => {
    const gross = this.projectedCombinedSubtotal() + this.projectedCombinedTax();
    return Math.max(0, gross - this.projectedCombinedDiscount());
  });

  setCartDiscountPercent(percent: number) {
    this.cartDiscountUserModified.set(true);
    this.cartCustomDiscountAmount.set(null);
    this.cartDiscountPercent.set(percent);
  }

  setCartDiscountAmount(amount: any) {
    this.cartDiscountUserModified.set(true);
    this.cartDiscountPercent.set(null);
    const val = Number(amount);
    this.cartCustomDiscountAmount.set(isNaN(val) ? 0 : Math.max(0, val));
  }

  isCartPercentActive(percent: number): boolean {
    if (this.cartCustomDiscountAmount() !== null) return false;
    const p = this.cartDiscountPercent();
    if (p !== null) return p === percent;
    if (!this.cartDiscountUserModified() && this.defaultDiscountPercentage() > 0) {
      return Math.round(this.defaultDiscountPercentage() * 100) === percent;
    }
    return percent === 0;
  }

  activeDiscountLabel = computed(() => {
    const disc = this.tableActiveOrder() ? this.projectedCombinedDiscount() : this.cartDiscountAmount();
    if (disc <= 0) return '';
    if (this.cartCustomDiscountAmount() !== null) return '';
    const p = this.cartDiscountPercent();
    if (p !== null && p > 0) return `(${p}%)`;
    if (!this.cartDiscountUserModified() && this.defaultDiscountPercentage() > 0) {
      return `(${Math.round(this.defaultDiscountPercentage() * 100)}% Global)`;
    }
    return '';
  });

  resetCartDiscount() {
    this.cartDiscountUserModified.set(false);
    this.cartCustomDiscountAmount.set(null);
    if (this.defaultDiscountPercentage() > 0) {
      this.cartDiscountPercent.set(Math.round(this.defaultDiscountPercentage() * 100));
    } else {
      this.cartDiscountPercent.set(null);
    }
    this.showDiscountBox.set(false);
  }

  // Quick Bill & Settle State
  showQuickBillModal = signal<boolean>(false);
  quickBillPaymentMethod = signal<'CASH' | 'CARD' | 'UPI'>('CASH');
  quickBillDiscount = signal<number>(0);
  quickBillAmountTendered = signal<number>(0);
  isQuickBilling = signal<boolean>(false);

  quickBillSubtotal = computed(() => {
    if (this.tableActiveOrder()) {
      return this.existingTabSubtotal() + this.cartSubtotal();
    }
    return this.cartSubtotal();
  });

  quickBillTax = computed(() => {
    return this.quickBillSubtotal() * this.taxRate();
  });

  quickBillDiscountAmount = computed(() => {
    const discount = Math.max(0, this.quickBillDiscount() || 0);
    const maxAllowed = this.quickBillSubtotal() + this.quickBillTax();
    return Math.min(discount, maxAllowed);
  });

  quickBillGrandTotal = computed(() => {
    const total = (this.quickBillSubtotal() + this.quickBillTax()) - this.quickBillDiscountAmount();
    return Math.max(0, total);
  });

  quickBillChangeDue = computed(() => {
    const tendered = this.quickBillAmountTendered() || 0;
    const grandTotal = this.quickBillGrandTotal();
    return Math.max(0, tendered - grandTotal);
  });

  setQuickBillDiscountPercent(percent: number) {
    const base = this.quickBillSubtotal() + this.quickBillTax();
    if (percent <= 0) {
      this.quickBillDiscount.set(0);
    } else {
      const calculated = Math.round(base * (percent / 100));
      this.quickBillDiscount.set(calculated);
    }
    if (this.quickBillPaymentMethod() === 'CASH') {
      this.quickBillAmountTendered.set(this.quickBillGrandTotal());
    }
  }

  setQuickBillDiscountAmount(amount: any) {
    const val = Number(amount) || 0;
    this.quickBillDiscount.set(Math.max(0, val));
    if (this.quickBillPaymentMethod() === 'CASH') {
      this.quickBillAmountTendered.set(this.quickBillGrandTotal());
    }
  }

  ngOnInit() {
    this.posService.getAppConfig().subscribe({
      next: (cfg) => {
        if (cfg) {
          if (cfg.taxRate !== undefined) this.taxRate.set(Number(cfg.taxRate));
          if (cfg.taxName) this.taxName.set(cfg.taxName);
          if (cfg.restaurantName) this.restaurantName.set(cfg.restaurantName);
          if (cfg.gstNumber) this.gstNumber.set(cfg.gstNumber);
          if (cfg.thermalPrintingEnabled !== undefined) this.thermalPrintingEnabled.set(cfg.thermalPrintingEnabled);
          if (cfg.thermalPaperSize) this.thermalPaperSize.set(cfg.thermalPaperSize);
          if (cfg.defaultDiscountPercentage !== undefined && cfg.defaultDiscountPercentage !== null) {
            const rawVal = Number(cfg.defaultDiscountPercentage);
            const fraction = rawVal > 1 ? rawVal / 100 : rawVal;
            this.defaultDiscountPercentage.set(fraction);
            if (fraction > 0 && !this.cartDiscountUserModified()) {
              this.cartDiscountPercent.set(Math.round(fraction * 100));
            }
          }
        }
      },
      error: () => {}
    });

    // Debounced search for running tabs (350ms)
    this.runningTabsSearchSub = this.runningTabsSearchSubject.pipe(
      debounceTime(350),
      distinctUntilChanged()
    ).subscribe(query => {
      this.runningTabsSearch.set(query);
      this.loadRunningOrdersPage(0, false);
    });

    // Debounced global menu search (300ms)
    this.menuSearchSub = this.menuSearchSubject.pipe(
      debounceTime(300),
      distinctUntilChanged()
    ).subscribe(query => {
      if (query && query.trim()) {
        this.isLoadingMenu.set(true);
        this.menuService.searchAllItems(query.trim()).subscribe({
          next: (items) => {
            this.menuItems.set(items.filter(i => i.available !== false));
            this.isLoadingMenu.set(false);
          },
          error: () => this.isLoadingMenu.set(false)
        });
      } else {
        const catId = this.activeCategory() || (this.categories().length > 0 ? this.categories()[0].id : null);
        if (catId) this.selectCategory(catId);
      }
    });

    this.loadActiveCategories();

    if (this.isAdmin()) {
      this.searchOutlets('');
    } else {
      this.loadRunningOrders();
    }
  }

  searchOutlets(query: string = '') {
    this.outletsLoading.set(true);
    this.outletService.getActiveOutlets(query, 50).subscribe({
      next: (outletsList) => {
        this.outlets.set(outletsList || []);
        this.outletsLoading.set(false);
        const currentSelected = this.selectedOutletId();
        if (!currentSelected && outletsList && outletsList.length > 0) {
          const saved = sessionStorage.getItem('admin_selected_pos_outlet');
          if (saved && outletsList.some(o => o.id === saved)) {
            this.selectedOutletId.set(saved);
          } else if (outletsList[0].id) {
            this.selectedOutletId.set(outletsList[0].id);
            sessionStorage.setItem('admin_selected_pos_outlet', outletsList[0].id);
          }
        }
        this.loadRunningOrders();
      },
      error: () => {
        this.outletsLoading.set(false);
        this.loadRunningOrders();
      }
    });
  }

  onOutletChange(newOutletId: string) {
    this.selectedOutletId.set(newOutletId);
    sessionStorage.setItem('admin_selected_pos_outlet', newOutletId);
    this.cart.set([]);
    this.selectedTable.set(null);
    this.tableActiveOrder.set(null);
    this.showExistingItems.set(false);
    this.resetCartDiscount();
    this.loadRunningOrders();
  }

  ngOnDestroy() {
    this.runningTabsSearchSub?.unsubscribe();
    this.menuSearchSub?.unsubscribe();
  }

  loadRunningOrdersPage(page: number, append: boolean = false) {
    if (this.isLoadingRunningOrders() || this.isLoadingMoreRunningTabs()) return;

    if (append) {
      this.isLoadingMoreRunningTabs.set(true);
    } else {
      this.isLoadingRunningOrders.set(true);
    }

    const activeStatuses = ['SOFT_ORDER', 'CONFIRMED', 'PREPARING', 'READY', 'SERVED'];
    const orderType = this.runningTabsTypeFilter() === 'ALL' ? undefined : this.runningTabsTypeFilter();
    const search = this.runningTabsSearch();
    const outletId = this.isAdmin() && this.selectedOutletId() ? this.selectedOutletId() : undefined;

    this.posService.getPagedOrders({
      statuses: activeStatuses,
      orderType,
      search,
      outletId,
      page,
      size: this.runningTabsPageSize(),
      sortBy: 'createdAt',
      sortDirection: 'DESC'
    }).subscribe({
      next: (res) => {
        if (append) {
          this.runningOrders.update(prev => [...prev, ...res.content]);
          this.isLoadingMoreRunningTabs.set(false);
        } else {
          this.runningOrders.set(res.content);
          this.isLoadingRunningOrders.set(false);
        }
        this.runningTabsPage.set(res.pageNumber);
        this.runningTabsTotal.set(res.totalElements);
        this.runningTabsIsLast.set(res.last);
      },
      error: () => {
        this.isLoadingRunningOrders.set(false);
        this.isLoadingMoreRunningTabs.set(false);
      }
    });
  }

  loadMoreRunningTabs() {
    if (this.isLoadingRunningOrders() || this.isLoadingMoreRunningTabs() || this.runningTabsIsLast()) return;
    const nextPage = this.runningTabsPage() + 1;
    this.loadRunningOrdersPage(nextPage, true);
  }

  loadRunningOrders() {
    this.loadRunningOrdersPage(0, false);
  }

  onRunningTabsSearchInput(query: string) {
    this.runningTabsSearchSubject.next(query);
  }

  clearRunningTabsSearch() {
    this.runningTabsSearch.set('');
    this.runningTabsSearchSubject.next('');
  }

  setRunningTabsTypeFilter(filter: 'ALL' | 'DINE_IN' | 'TAKE_AWAY') {
    this.runningTabsTypeFilter.set(filter);
    this.loadRunningOrdersPage(0, false);
  }

  onMenuSearchInput(query: string) {
    this.searchQuery.set(query);
    this.menuSearchSubject.next(query);
  }

  clearMenuSearch() {
    this.searchQuery.set('');
    this.menuSearchSubject.next('');
  }

  loadActiveCategories() {
    this.menuService.getCategories(null, true).subscribe({
      next: (cats) => {
        this.categories.set(cats);
        if (cats.length > 0) this.selectCategory(cats[0].id);
      },
      error: () => this.alertService.error('Failed to load categories')
    });
  }

  selectCategory(categoryId: string) {
    this.activeCategory.set(categoryId);
    this.isLoadingMenu.set(true);
    this.menuService.getItemsByCategory(categoryId, null).subscribe({
      next: (items) => {
        this.menuItems.set(items.filter(i => i.available !== false)); 
        this.isLoadingMenu.set(false);
      },
      error: () => {
        this.alertService.error('Failed to load menu items');
        this.isLoadingMenu.set(false);
      }
    });
  }

  // --- Cart Management ---
  addToCart(item: ItemResponse) {
    this.cart.update(current => {
      const existing = current.find(i => i.itemId === item.id);
      if (existing) {
        return current.map(i => i.itemId === item.id ? { ...i, quantity: i.quantity + 1 } : i);
      }
      return [...current, { itemId: item.id, name: item.name, price: item.price, quantity: 1 }];
    });
  }

  updateQuantity(itemId: string, delta: number) {
    this.cart.update(current => {
      return current.map(i => {
        if (i.itemId === itemId) {
          const newQ = i.quantity + delta;
          return { ...i, quantity: newQ };
        }
        return i;
      }).filter(i => i.quantity > 0);
    });
  }

  removeItem(itemId: string) {
    this.cart.update(current => current.filter(i => i.itemId !== itemId));
  }

  openNoteModal(cartItem: CartItem) {
    this.selectedItemForNote.set(cartItem);
    this.noteInput.set(cartItem.notes || '');
  }

  saveNote() {
    const item = this.selectedItemForNote();
    if (!item) return;
    this.cart.update(current =>
      current.map(i => i.itemId === item.itemId ? { ...i, notes: this.noteInput() } : i)
    );
    this.selectedItemForNote.set(null);
  }

  clearCart() {
    this.cart.set([]);
    this.resetCartDiscount();
    this.selectedTable.set(null);
    this.tableActiveOrder.set(null);
  }

  removeItemFromCart(index: number) {
    this.cart.update(items => items.filter((_, i) => i !== index));
  }

  // --- Table Selection ---
  async voidExistingItem(orderItemId: string) {
    const order = this.tableActiveOrder();
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
      next: (updatedOrder) => {
        this.alertService.success('Item voided from order');
        this.tableActiveOrder.set(updatedOrder);
      },
      error: () => this.alertService.error('Failed to void item')
    });
  }

  openTableSelection() {
    this.orderType.set('DINE_IN');
    this.showTableModal.set(true);
    this.isLoadingTables.set(true);
    
    const outletId = this.isAdmin() && this.selectedOutletId() ? this.selectedOutletId() : undefined;
    this.posService.getTables(outletId).subscribe({
      next: (data) => {
        this.tables.set(data);
        this.isLoadingTables.set(false);
      },
      error: () => {
        this.alertService.error('Failed to load tables');
        this.isLoadingTables.set(false);
      }
    });
  }

  openRunningTabsModal() {
    this.runningTabsSearch.set('');
    this.runningTabsTypeFilter.set('ALL');
    this.loadRunningOrders();
    this.showRunningTabsModal.set(true);
  }

  selectRunningTab(order: OrderResponse) {
    this.tableActiveOrder.set(order);
    this.showRunningTabsModal.set(false);

    if (order.discountAmount > 0) {
      this.cartCustomDiscountAmount.set(order.discountAmount);
      this.cartDiscountPercent.set(null);
      this.cartDiscountUserModified.set(true);
    } else {
      this.resetCartDiscount();
    }

    if (order.orderType === 'DINE_IN') {
      this.orderType.set('DINE_IN');
      if (order.tableId) {
        this.selectedTable.set({
          id: order.tableId,
          tableNumber: order.tableNumber || '?',
          qrToken: '',
          status: 'OCCUPIED'
        });
      }
    } else {
      this.orderType.set('TAKE_AWAY');
      this.selectedTable.set(null);
    }

    const discInfo = order.discountAmount > 0 ? `, Discount: ₹${order.discountAmount.toFixed(2)}` : '';
    this.alertService.info(`Tab #${order.orderNumber} loaded (${order.items?.length || 0} items, ₹${order.subTotal.toFixed(2)}${discInfo}). New dishes will append to this bill.`);
  }

  detachActiveTab() {
    this.tableActiveOrder.set(null);
    this.resetCartDiscount();
    this.showExistingItems.set(false);
    this.alertService.info('Switched back to new order mode');
  }

  selectTable(table: TableResponse) {
    this.selectedTable.set(table);
    this.showTableModal.set(false);

    // If occupied, check active order
    if (table.status === 'OCCUPIED') {
      const existing = this.runningOrders().find(o => o.tableId === table.id || (table.tableNumber && o.tableNumber === table.tableNumber));
      if (existing) {
        this.tableActiveOrder.set(existing);
        this.alertService.info(`${table.tableNumber} has active Tab #${existing.orderNumber}. New items will append to it.`);
      } else {
        this.posService.getActiveOrderByQrToken(table.qrToken).subscribe({
          next: (order) => {
            if (order && order.id) {
              this.tableActiveOrder.set(order);
              this.alertService.info(`${table.tableNumber} has active Tab #${order.orderNumber}. New items will append to it.`);
            }
          },
          error: () => this.tableActiveOrder.set(null)
        });
      }
    } else {
      this.tableActiveOrder.set(null);
    }
  }

  // --- Checkout Workflow ---
  placeOrder() {
    if (this.cart().length === 0) return;

    if (this.orderType() === 'DINE_IN' && !this.selectedTable()) {
      this.alertService.warning('Please select a table for Dine-In.');
      this.openTableSelection();
      return;
    }

    this.isProcessing.set(true);
    const orderRequests: OrderItemRequest[] = this.cart().map(item => ({
      itemId: item.itemId,
      quantity: item.quantity,
      notes: item.notes
    }));

    const activeOrder = this.tableActiveOrder();
    const outletId = this.isAdmin() && this.selectedOutletId() ? this.selectedOutletId() : undefined;
    const request$ = activeOrder
      ? this.posService.appendItemsToOrder(activeOrder.id, orderRequests)
      : (this.orderType() === 'TAKE_AWAY'
          ? this.posService.createTakeAwayOrder(orderRequests, outletId)
          : this.posService.createPosDineInOrder(this.selectedTable()!.id, orderRequests));

    request$.subscribe({
      next: (res) => {
        if (activeOrder) {
          this.alertService.success(`Added ${orderRequests.length} item(s) to Tab #${res.orderNumber}! Sent to kitchen.`, 'Tab Updated');
        } else {
          this.alertService.success(`Order #${res.orderNumber} sent to kitchen!`, 'Order Placed');
        }
        this.cart.set([]); 
        this.selectedTable.set(null);
        this.tableActiveOrder.set(null);
        this.showExistingItems.set(false);
        this.isProcessing.set(false);
        this.loadRunningOrders();
      },
      error: (err) => {
        this.alertService.error(err?.error?.error || 'Failed to process order');
        this.isProcessing.set(false);
      }
    });
  }

  // --- Quick Bill & Settle Payment Workflow ---
  openQuickBillModal() {
    if (this.cart().length === 0 && !this.tableActiveOrder()) {
      this.alertService.warning('Please add items to cart first.');
      return;
    }

    if (this.orderType() === 'DINE_IN' && !this.selectedTable() && !this.tableActiveOrder()) {
      this.alertService.warning('Please select a table for Dine-In first.');
      this.openTableSelection();
      return;
    }

    if (this.cartDiscountAmount() > 0) {
      this.quickBillDiscount.set(this.cartDiscountAmount());
    } else if (this.existingTabDiscount() > 0) {
      this.quickBillDiscount.set(this.existingTabDiscount());
    } else if (this.defaultDiscountPercentage() > 0) {
      this.quickBillDiscount.set(
        Math.round((this.quickBillSubtotal() + this.quickBillTax()) * this.defaultDiscountPercentage())
      );
    } else {
      this.quickBillDiscount.set(0);
    }

    this.quickBillPaymentMethod.set('CASH');
    this.quickBillAmountTendered.set(this.quickBillGrandTotal());
    this.showQuickBillModal.set(true);
  }

  setQuickCashTendered(amount: number) {
    this.quickBillAmountTendered.set(amount);
  }

  setExactCashTendered() {
    this.quickBillAmountTendered.set(this.quickBillGrandTotal());
  }

  confirmQuickBillAndPay() {
    const grandTotal = this.quickBillGrandTotal();
    const method = this.quickBillPaymentMethod();
    const tendered = this.quickBillAmountTendered();

    if (method === 'CASH' && tendered < grandTotal) {
      this.alertService.warning(`Amount tendered (₹${tendered.toFixed(2)}) is less than total bill (₹${grandTotal.toFixed(2)}).`);
      return;
    }

    const changeDueAmount = this.quickBillChangeDue();

    this.isQuickBilling.set(true);

    const orderRequests: OrderItemRequest[] = this.cart().map(item => ({
      itemId: item.itemId,
      quantity: item.quantity,
      notes: item.notes
    }));

    const activeOrder = this.tableActiveOrder();

    const completeBillAndSettlement = (orderId: string, orderNumber: string) => {
      const discount = this.quickBillDiscountAmount();
      this.billingService.generateBill(orderId, discount).subscribe({
        next: (billedOrder) => {
          const finalAmount = billedOrder.grandTotal > 0 ? billedOrder.grandTotal : grandTotal;
          this.billingService.settlePayment(orderId, method, finalAmount).subscribe({
            next: (payment) => {
              this.isQuickBilling.set(false);
              this.showQuickBillModal.set(false);
              this.cart.set([]);
              this.resetCartDiscount();
              this.selectedTable.set(null);
              this.tableActiveOrder.set(null);
              this.showExistingItems.set(false);
              this.loadRunningOrders();

              if (payment && payment.settledBy) {
                billedOrder.settledBy = payment.settledBy;
              }

              const discountMsg = discount > 0 ? ` [Discount: -₹${discount.toFixed(2)}]` : '';
              const changeMsg = (method === 'CASH' && changeDueAmount > 0)
                ? ` | Change Due: ₹${changeDueAmount.toFixed(2)}`
                : '';
              this.alertService.success(
                `Order #${orderNumber} billed & paid via ${method} (₹${finalAmount.toFixed(2)})${discountMsg}${changeMsg}! Sent to kitchen queue.`,
                'Payment Collected & Order Placed'
              );

              // Auto-print receipt if thermal printing is enabled in System Settings
              if (this.thermalPrintingEnabled()) {
                this.receiptOrder.set(billedOrder);
                this.showReceiptModal.set(true);
                setTimeout(() => {
                  this.printReceipt();
                }, 350);
              }
            },
            error: (payErr) => {
              this.isQuickBilling.set(false);
              this.alertService.error(payErr?.error?.error || 'Failed to record payment');
            }
          });
        },
        error: (billErr) => {
          this.isQuickBilling.set(false);
          this.alertService.error(billErr?.error?.error || 'Failed to generate bill');
        }
      });
    };

    if (activeOrder) {
      if (orderRequests.length > 0) {
        this.posService.appendItemsToOrder(activeOrder.id, orderRequests).subscribe({
          next: (updatedOrder) => {
            completeBillAndSettlement(updatedOrder.id, updatedOrder.orderNumber);
          },
          error: (err) => {
            this.isQuickBilling.set(false);
            this.alertService.error(err?.error?.error || 'Failed to append items before billing');
          }
        });
      } else {
        completeBillAndSettlement(activeOrder.id, activeOrder.orderNumber);
      }
    } else {
      const outletId = this.isAdmin() && this.selectedOutletId() ? this.selectedOutletId() : undefined;
      const createOrder$ = this.orderType() === 'TAKE_AWAY'
        ? this.posService.createTakeAwayOrder(orderRequests, outletId)
        : this.posService.createPosDineInOrder(this.selectedTable()!.id, orderRequests);

      createOrder$.subscribe({
        next: (newOrder) => {
          completeBillAndSettlement(newOrder.id, newOrder.orderNumber);
        },
        error: (err) => {
          this.isQuickBilling.set(false);
          this.alertService.error(err?.error?.error || 'Failed to create order');
        }
      });
    }
  }

  goToBilling() {
    this.router.navigate(['/billing']);
  }

  getStatusBadgeClass(status?: string): string {
    switch (status) {
      case 'SOFT_ORDER':
        return 'bg-amber-100 dark:bg-amber-950/80 text-amber-800 dark:text-amber-300 border-amber-300 dark:border-amber-800';
      case 'CONFIRMED':
        return 'bg-blue-100 dark:bg-blue-950/80 text-blue-800 dark:text-blue-300 border-blue-300 dark:border-blue-800';
      case 'PREPARING':
        return 'bg-purple-100 dark:bg-purple-950/80 text-purple-800 dark:text-purple-300 border-purple-300 dark:border-purple-800 animate-pulse';
      case 'READY':
        return 'bg-emerald-100 dark:bg-emerald-950/80 text-emerald-800 dark:text-emerald-300 border-emerald-300 dark:border-emerald-800 font-bold';
      case 'SERVED':
        return 'bg-teal-100 dark:bg-teal-950/80 text-teal-800 dark:text-teal-300 border-teal-300 dark:border-teal-800';
      default:
        return 'bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 border-slate-300 dark:border-slate-700';
    }
  }

  getElapsedMinutes(createdAt?: string): number {
    if (!createdAt) return 0;
    const diffMs = Date.now() - new Date(createdAt).getTime();
    return Math.max(0, Math.floor(diffMs / 60000));
  }
}