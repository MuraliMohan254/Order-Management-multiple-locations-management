import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { ToastrService } from 'ngx-toastr';
import { MenuService } from '../../services/menu.service';
import { CategoryResponse, ItemResponse } from '../../models/menu.models';

@Component({
  selector: 'app-menu-management',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './menu-management.html'
})
export class MenuManagementComponent implements OnInit {
  private menuService = inject(MenuService);
  private toastr = inject(ToastrService);
  private fb = inject(FormBuilder);

  categories = signal<CategoryResponse[]>([]);
  items = signal<ItemResponse[]>([]);
  selectedCategory = signal<CategoryResponse | null>(null);
  
  isLoadingCategories = signal(false);
  isLoadingItems = signal(false);
  categoryFilter = signal<'ACTIVE' | 'INACTIVE'>('ACTIVE'); 

  categorySearchCtrl = new FormControl('');
  itemSearchCtrl = new FormControl('');

  showCategoryModal = signal(false);
  showItemModal = signal(false);
  editingCategoryId = signal<string | null>(null);
  editingItemId = signal<string | null>(null);

  categoryForm = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(3)]],
    active: [true]
  });

  itemForm = this.fb.nonNullable.group({
    categoryId: ['', [Validators.required]], // Added Category Selection
    name: ['', [Validators.required, Validators.minLength(3)]],
    price: [0, [Validators.required, Validators.min(0.01)]],
    available: [true]
  });

  ngOnInit() {
    this.loadCategories();

    this.categorySearchCtrl.valueChanges.pipe(
      debounceTime(400),
      distinctUntilChanged()
    ).subscribe(val => this.loadCategories(val));

    this.itemSearchCtrl.valueChanges.pipe(
      debounceTime(400),
      distinctUntilChanged()
    ).subscribe(val => {
      const currentCat = this.selectedCategory();
      if (currentCat) this.loadItems(currentCat.id, val);
    });
  }

  // ==========================================
  // CATEGORY OPERATIONS
  // ==========================================

  loadCategories(search?: string | null) {
    this.isLoadingCategories.set(true);
    const isActiveTab = this.categoryFilter() === 'ACTIVE';
    
    this.menuService.getCategories(search, isActiveTab).subscribe({
      next: (data) => {
        this.categories.set(data);
        
        const selectionExists = data.some(c => c.id === this.selectedCategory()?.id);
        if (!selectionExists && data.length > 0) {
          this.selectCategory(data[0]);
        } else if (!selectionExists) {
          this.selectedCategory.set(null);
          this.items.set([]);
        }
        
        this.isLoadingCategories.set(false);
      },
      error: () => {
        this.toastr.error('Failed to load categories');
        this.isLoadingCategories.set(false);
      }
    });
  }

  switchCategoryTab(tab: 'ACTIVE' | 'INACTIVE') {
    if (this.categoryFilter() === tab) return;
    this.categoryFilter.set(tab);
    this.loadCategories(this.categorySearchCtrl.value);
  }

  selectCategory(category: CategoryResponse) {
    this.selectedCategory.set(category);
    this.itemSearchCtrl.setValue('', { emitEvent: false });
    this.loadItems(category.id);
  }

  openCategoryModal(category?: CategoryResponse) {
    if (category) {
      this.editingCategoryId.set(category.id);
      this.categoryForm.patchValue({ 
        name: category.name, 
        active: category.active 
      });
    } else {
      this.editingCategoryId.set(null);
      this.categoryForm.reset({ name: '', active: true });
    }
    this.showCategoryModal.set(true);
  }

  saveCategory() {
    if (this.categoryForm.invalid) return;
    const req = this.categoryForm.getRawValue();
    const id = this.editingCategoryId();

    const request$ = id 
      ? this.menuService.updateCategory(id, req)
      : this.menuService.createCategory(req);

    request$.subscribe({
      next: () => {
        this.toastr.success(`Category ${id ? 'updated' : 'created'} successfully`);
        this.showCategoryModal.set(false);
        this.loadCategories(this.categorySearchCtrl.value);
      },
      error: () => this.toastr.error('Operation failed')
    });
  }

  deleteCategory(id: string, event: Event) {
    event.stopPropagation(); // Prevents selecting the category when clicking delete
    if (!confirm('Are you sure you want to delete this category?')) return;
    
    this.menuService.deleteCategory(id).subscribe({
      next: () => {
        this.toastr.success('Category deleted');
        if (this.selectedCategory()?.id === id) this.selectedCategory.set(null);
        this.loadCategories(this.categorySearchCtrl.value);
      },
      error: () => this.toastr.error('Failed to delete category')
    });
  }

  // ==========================================
  // ITEM OPERATIONS
  // ==========================================

  loadItems(categoryId: string, search?: string | null) {
    this.isLoadingItems.set(true);
    this.menuService.getItemsByCategory(categoryId, search).subscribe({
      next: (data) => {
        this.items.set(data);
        this.isLoadingItems.set(false);
      },
      error: () => {
        this.toastr.error('Failed to load items');
        this.isLoadingItems.set(false);
      }
    });
  }

  openItemModal(item?: ItemResponse) {
    if (item) {
      this.editingItemId.set(item.id);
      this.itemForm.patchValue({ 
        categoryId: item.categoryId,
        name: item.name, 
        price: item.price, 
        available: item.available 
      });
    } else {
      this.editingItemId.set(null);
      this.itemForm.reset({ 
        categoryId: this.selectedCategory()?.id || '', // Default to current
        name: '', 
        price: 0, 
        available: true 
      });
    }
    this.showItemModal.set(true);
  }

  saveItem() {
    if (this.itemForm.invalid) return;
    const formVals = this.itemForm.getRawValue();
    const id = this.editingItemId();
    
    const req = { 
      categoryId: formVals.categoryId, // Grab category assignment from form
      name: formVals.name, 
      price: formVals.price,
      available: formVals.available
    };

    const request$ = id 
      ? this.menuService.updateItem(id, req)
      : this.menuService.createItem(req);

    request$.subscribe({
      next: () => {
        this.toastr.success(`Item ${id ? 'updated' : 'created'} successfully`);
        this.showItemModal.set(false);
        // Refresh items for the current tab (the item will cleanly vanish if moved out of this category)
        if (this.selectedCategory()) {
           this.loadItems(this.selectedCategory()!.id, this.itemSearchCtrl.value);
        }
      },
      error: () => this.toastr.error('Operation failed')
    });
  }

  deleteItem(id: string, event: Event) {
    event.stopPropagation();
    if (!confirm('Are you sure you want to delete this item?')) return;

    this.menuService.deleteItem(id).subscribe({
      next: () => {
        this.toastr.success('Item deleted');
        if (this.selectedCategory()) {
          this.loadItems(this.selectedCategory()!.id, this.itemSearchCtrl.value);
        }
      },
      error: () => this.toastr.error('Failed to delete item')
    });
  }
}