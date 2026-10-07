import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ToastrService } from 'ngx-toastr';
import { PosService } from '../../../services/pos/pos.service';
import { MenuService } from '../../../services/menu.service';
import { CategoryResponse, ItemResponse } from '../../../models/menu.models';
import { CartItem, OrderItemRequest, TableResponse } from '../../../models/pos/pos.models';

@Component({
  selector: 'app-pos-terminal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './pos-terminal.html'
})
export class PosTerminalComponent implements OnInit {
  private posService = inject(PosService);
  private menuService = inject(MenuService);
  private toastr = inject(ToastrService);

  // Menu State
  categories = signal<CategoryResponse[]>([]);
  activeCategory = signal<string | null>(null);
  menuItems = signal<ItemResponse[]>([]);
  isLoadingMenu = signal(false);

  // Cart & Order State
  cart = signal<CartItem[]>([]);
  orderType = signal<'TAKE_AWAY' | 'DINE_IN'>('TAKE_AWAY');
  isProcessing = signal(false);

  // Table State (New)
  tables = signal<TableResponse[]>([]);
  selectedTable = signal<TableResponse | null>(null);
  showTableModal = signal(false);
  isLoadingTables = signal(false);

  cartTotal = computed(() => {
    return this.cart().reduce((sum, item) => sum + (item.price * item.quantity), 0);
  });

  ngOnInit() {
    this.loadActiveCategories();
  }

  loadActiveCategories() {
    this.menuService.getCategories(null, true).subscribe({
      next: (cats) => {
        this.categories.set(cats);
        if (cats.length > 0) this.selectCategory(cats[0].id);
      },
      error: () => this.toastr.error('Failed to load categories')
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
        this.toastr.error('Failed to load menu items');
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
          return newQ > 0 ? { ...i, quantity: newQ } : i;
        }
        return i;
      }).filter(i => i.quantity > 0);
    });
  }

  clearCart() {
    if (confirm('Clear entire cart?')) {
      this.cart.set([]);
      this.selectedTable.set(null); // Clear table selection too
    }
  }

  // --- Table Management (New) ---
  openTableSelection() {
    this.orderType.set('DINE_IN');
    this.showTableModal.set(true);
    this.isLoadingTables.set(true);
    
    this.posService.getTables().subscribe({
      next: (data) => {
        this.tables.set(data);
        this.isLoadingTables.set(false);
      },
      error: () => {
        this.toastr.error('Failed to load tables');
        this.isLoadingTables.set(false);
      }
    });
  }

  selectTable(table: TableResponse) {
    this.selectedTable.set(table);
    this.showTableModal.set(false);
  }

  // --- Checkout Workflow ---
  placeOrder() {
    if (this.cart().length === 0) return;

    // Validate table selection for Dine-In
    if (this.orderType() === 'DINE_IN' && !this.selectedTable()) {
      this.toastr.warning('Please select a table first.');
      this.openTableSelection();
      return;
    }

    this.isProcessing.set(true);
    const orderRequests: OrderItemRequest[] = this.cart().map(item => ({
      itemId: item.itemId,
      quantity: item.quantity,
      notes: item.notes
    }));

    const request$ = this.orderType() === 'TAKE_AWAY'
      ? this.posService.createTakeAwayOrder(orderRequests)
      : this.posService.createPosDineInOrder(this.selectedTable()!.id, orderRequests);

    request$.subscribe({
      next: (res) => {
        this.toastr.success(`Order ${res.orderNumber} sent to kitchen!`);
        this.cart.set([]); 
        this.selectedTable.set(null); // Reset after successful order
        this.isProcessing.set(false);
      },
      error: () => {
        this.toastr.error('Failed to process order');
        this.isProcessing.set(false);
      }
    });
  }
}