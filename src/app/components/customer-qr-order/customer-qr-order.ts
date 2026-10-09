import { Component, OnInit, OnDestroy, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { AlertService } from '../../services/alert.service';
import { PosService } from '../../services/pos/pos.service';
import { MenuService } from '../../services/menu.service';
import { CategoryResponse, ItemResponse } from '../../models/menu.models';
import { CartItem, OrderItemRequest, OrderResponse, TableResponse } from '../../models/pos/pos.models';
import { ThemeService } from '../../services/theme.service';

@Component({
  selector: 'app-customer-qr-order',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './customer-qr-order.html',
  styleUrls: ['./customer-qr-order.css']
})
export class CustomerQrOrderComponent implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private posService = inject(PosService);
  private menuService = inject(MenuService);
  private alertService = inject(AlertService);
  public themeService = inject(ThemeService);

  qrToken = signal<string>('');
  tableInfo = signal<TableResponse | null>(null);
  activeOrder = signal<OrderResponse | null>(null);
  
  // Loading & View States
  isLoadingTable = signal(true);
  isInvalidQr = signal(false);
  activeView = signal<'MENU' | 'TRACKER'>('MENU');
  
  // Menu State
  categories = signal<CategoryResponse[]>([]);
  activeCategoryId = signal<string>('ALL');
  allItems = signal<ItemResponse[]>([]);
  searchQuery = signal<string>('');
  isLoadingMenu = signal(false);

  // Cart State
  cart = signal<CartItem[]>([]);
  showCartModal = signal(false);
  isSubmittingOrder = signal(false);

  // Item Note Modal
  selectedItemForNote = signal<CartItem | null>(null);
  itemNoteInput = signal<string>('');

  private pollIntervalId: any = null;

  filteredItems = computed(() => {
    let list = this.allItems();
    const catId = this.activeCategoryId();
    const query = this.searchQuery().trim().toLowerCase();

    if (catId !== 'ALL') {
      list = list.filter(item => item.categoryId === catId);
    }

    if (query) {
      list = list.filter(item => 
        item.name.toLowerCase().includes(query)
      );
    }

    return list;
  });

  cartCount = computed(() => {
    return this.cart().reduce((sum, item) => sum + item.quantity, 0);
  });

  cartSubtotal = computed(() => {
    return this.cart().reduce((sum, item) => sum + (item.price * item.quantity), 0);
  });

  taxRate = signal<number>(0.05);
  taxName = signal<string>('GST');

  cartEstimatedTax = computed(() => {
    return this.cartSubtotal() * this.taxRate();
  });

  cartEstimatedTotal = computed(() => {
    return this.cartSubtotal() + this.cartEstimatedTax();
  });

  ngOnInit() {
    this.posService.getAppConfig().subscribe({
      next: (cfg) => {
        if (cfg) {
          if (cfg.taxRate !== undefined) this.taxRate.set(Number(cfg.taxRate));
          if (cfg.taxName) this.taxName.set(cfg.taxName);
        }
      },
      error: () => {}
    });

    this.route.params.subscribe(params => {
      const token = params['qrToken'];
      if (token) {
        this.qrToken.set(token);
        this.loadTableAndOrder(token);
          this.loadCart(token);
        this.loadMenuData();
        this.startStatusPolling();
      } else {
        this.isInvalidQr.set(true);
        this.isLoadingTable.set(false);
      }
    });
  }

  ngOnDestroy() {
    if (this.pollIntervalId) {
      clearInterval(this.pollIntervalId);
    }
  }

  loadTableAndOrder(token: string) {
    this.isLoadingTable.set(true);
    this.posService.getTableByQrToken(token).subscribe({
      next: (table) => {
        this.tableInfo.set(table);
        this.isInvalidQr.set(false);
        this.isLoadingTable.set(false);
        this.checkActiveOrder(token);
      },
      error: () => {
        this.isInvalidQr.set(true);
        this.isLoadingTable.set(false);
      }
    });
  }

  checkActiveOrder(token: string) {
    this.posService.getActiveOrderByQrToken(token).subscribe({
      next: (order) => {
        if (order && order.id) {
          this.activeOrder.set(order);
          // If customer has an active order and cart is empty, start in tracker view
          if (this.cart().length === 0 && !this.activeOrder()) {
            this.activeView.set('TRACKER');
          }
        } else {
          this.activeOrder.set(null);
        }
      },
      error: () => {
        // If 204 No Content or error, no active order
        this.activeOrder.set(null);
      }
    });
  }

  startStatusPolling() {
    this.pollIntervalId = setInterval(() => {
      const token = this.qrToken();
      if (token && !this.isInvalidQr()) {
        this.posService.getActiveOrderByQrToken(token).subscribe({
          next: (order) => {
            if (order && order.id) {
              this.activeOrder.set(order);
            } else {
              this.activeOrder.set(null);
            }
          },
          error: () => {}
        });
      }
    }, 6000);
  }

  loadMenuData() {
    this.isLoadingMenu.set(true);
    this.menuService.getCategories(null, true).subscribe({
      next: (cats) => {
        this.categories.set(cats);
        // Load items for each category
        this.loadAllCategoryItems(cats);
      },
      error: () => {
        this.alertService.error('Could not load menu categories');
        this.isLoadingMenu.set(false);
      }
    });
  }

  loadAllCategoryItems(cats: CategoryResponse[]) {
    let itemsAccumulator: ItemResponse[] = [];
    if (cats.length === 0) {
      this.isLoadingMenu.set(false);
      return;
    }

    let loadedCount = 0;
    cats.forEach(cat => {
      this.menuService.getItemsByCategory(cat.id, null).subscribe({
        next: (items) => {
          const availableItems = items
            .filter(i => i.available !== false)
            .map(i => ({ ...i, categoryId: cat.id }));
          itemsAccumulator = [...itemsAccumulator, ...availableItems];
          loadedCount++;
          if (loadedCount === cats.length) {
            this.allItems.set(itemsAccumulator);
            this.isLoadingMenu.set(false);
          }
        },
        error: () => {
          loadedCount++;
          if (loadedCount === cats.length) {
            this.allItems.set(itemsAccumulator);
            this.isLoadingMenu.set(false);
          }
        }
      });
    });
  }



  saveCart(cartData: CartItem[]) {
    if (this.qrToken()) {
      localStorage.setItem(`wb_cart_${this.qrToken()}`, JSON.stringify(cartData));
    }
  }

  loadCart(token: string) {
    try {
      const saved = localStorage.getItem(`wb_cart_${token}`);
      if (saved) {
        this.cart.set(JSON.parse(saved));
      }
    } catch {
      // ignore
    }
  }

  addToCart(item: ItemResponse) {
    this.cart.update(current => {
      const existing = current.find(i => i.itemId === item.id);
      let newCart;
      if (existing) {
        newCart = current.map(i => i.itemId === item.id ? { ...i, quantity: i.quantity + 1 } : i);
      } else {
        newCart = [...current, { itemId: item.id, name: item.name, price: item.price, quantity: 1 }];
      }
      this.saveCart(newCart);
      return newCart;
    });
    this.alertService.success(`${item.name} added to cart`);
  }

  getItemQuantityInCart(itemId: string): number {
    return this.cart().find(i => i.itemId === itemId)?.quantity || 0;
  }

  updateQuantity(itemId: string, delta: number) {
    this.cart.update(current => {
      const newCart = current.map(i => {
        if (i.itemId === itemId) {
          return { ...i, quantity: i.quantity + delta };
        }
        return i;
      }).filter(i => i.quantity > 0);
      this.saveCart(newCart);
      return newCart;
    });
  }

  openNoteModal(cartItem: CartItem) {
    this.selectedItemForNote.set(cartItem);
    this.itemNoteInput.set(cartItem.notes || '');
  }

  saveItemNote() {
    const item = this.selectedItemForNote();
    if (!item) return;
    
    this.cart.update(current => 
      current.map(i => i.itemId === item.itemId ? { ...i, notes: this.itemNoteInput() } : i)
    );
    this.selectedItemForNote.set(null);
  }

  placeOrder() {
    if (this.cart().length === 0) return;
    const token = this.qrToken();
    if (!token) return;

    this.isSubmittingOrder.set(true);
    const orderItems: OrderItemRequest[] = this.cart().map(item => ({
      itemId: item.itemId,
      quantity: item.quantity,
      notes: item.notes
    }));

    this.posService.processQrOrder(token, orderItems).subscribe({
      next: (res) => {
        this.alertService.success(`Order ${res.orderNumber} sent to kitchen!`, 'Order Confirmed');
        this.activeOrder.set(res);
        this.cart.set([]);
          this.saveCart([]);
        this.showCartModal.set(false);
        this.activeView.set('TRACKER');
        this.isSubmittingOrder.set(false);
      },
      error: () => {
        this.alertService.error('Failed to place order. Please call a waiter.');
        this.isSubmittingOrder.set(false);
      }
    });
  }

  getStatusBadgeClass(status?: string): string {
    switch (status) {
      case 'SOFT_ORDER':
        return 'bg-amber-100 text-amber-800 border-amber-300';
      case 'CONFIRMED':
        return 'bg-blue-100 text-blue-800 border-blue-300';
      case 'PREPARING':
        return 'bg-purple-100 text-purple-800 border-purple-300 animate-pulse';
      case 'READY':
        return 'bg-emerald-100 text-emerald-800 border-emerald-300 font-bold';
      case 'SERVED':
        return 'bg-teal-100 text-teal-800 border-teal-300';
      case 'BILLED':
        return 'bg-slate-100 text-slate-800 border-slate-300';
      case 'CANCELLED':
        return 'bg-rose-100 text-rose-800 border-rose-300';
      default:
        return 'bg-slate-100 text-slate-700 border-slate-200';
    }
  }

  getStatusStepIndex(status?: string): number {
    switch (status) {
      case 'CONFIRMED':
      case 'SOFT_ORDER':
        return 1;
      case 'PREPARING':
        return 2;
      case 'READY':
        return 3;
      case 'SERVED':
      case 'BILLED':
        return 4;
      default:
        return 1;
    }
  }
}
