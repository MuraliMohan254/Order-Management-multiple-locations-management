import { Component, Input, Output, EventEmitter, ElementRef, HostListener, signal, computed, ViewChild, forwardRef, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { Subject, Subscription } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';

export interface SelectOption {
  value: any;
  label: string;
  sublabel?: string;
  badge?: string;
  icon?: string;
}

@Component({
  selector: 'app-custom-select',
  standalone: true,
  imports: [CommonModule, FormsModule],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => CustomSelectComponent),
      multi: true
    }
  ],
  styles: [`
    .custom-scrollbar {
      scrollbar-width: thin;
      scrollbar-color: #cbd5e1 transparent;
    }
    :host-context([data-theme="dark"]) .custom-scrollbar,
    :host-context(.dark) .custom-scrollbar {
      scrollbar-color: #475569 transparent;
    }
    .custom-scrollbar::-webkit-scrollbar {
      width: 6px;
      height: 6px;
    }
    .custom-scrollbar::-webkit-scrollbar-track {
      background: transparent;
    }
    .custom-scrollbar::-webkit-scrollbar-thumb {
      background-color: #cbd5e1;
      border-radius: 9999px;
    }
    .custom-scrollbar::-webkit-scrollbar-thumb:hover {
      background-color: #94a3b8;
    }
    :host-context([data-theme="dark"]) .custom-scrollbar::-webkit-scrollbar-thumb,
    :host-context(.dark) .custom-scrollbar::-webkit-scrollbar-thumb {
      background-color: #475569;
    }
    :host-context([data-theme="dark"]) .custom-scrollbar::-webkit-scrollbar-thumb:hover,
    :host-context(.dark) .custom-scrollbar::-webkit-scrollbar-thumb:hover {
      background-color: #64748b;
    }
  `],
  template: `
    <div class="relative w-full" (click)="$event.stopPropagation()">
      <!-- Trigger Button -->
      <button
        type="button"
        (click)="toggleOpen()"
        [disabled]="disabled"
        class="w-full flex items-center justify-between gap-2 px-3 py-2 text-xs font-bold rounded-xl border transition-all text-left outline-none cursor-pointer"
        [ngClass]="{
          'bg-slate-50 dark:bg-slate-900 border-slate-200 dark:border-slate-700 text-slate-800 dark:text-slate-100 hover:border-slate-300 dark:hover:border-slate-600': !isOpen() && !disabled,
          'bg-white dark:bg-slate-800 border-blue-500 ring-2 ring-blue-500/20 text-slate-900 dark:text-white': isOpen() && !disabled,
          'opacity-50 cursor-not-allowed bg-slate-100 dark:bg-slate-800': disabled
        }"
      >
        <div class="flex items-center gap-2 truncate">
          @if (selectedOption()?.icon) {
            <span class="text-sm shrink-0">{{ selectedOption()?.icon }}</span>
          }
          <span class="truncate">
            {{ selectedOption() ? selectedOption()!.label : placeholder }}
          </span>
          @if (selectedOption()?.badge) {
            <span class="px-1.5 py-0.5 rounded text-[10px] font-bold bg-blue-100 dark:bg-blue-900/40 text-blue-700 dark:text-blue-300">
              {{ selectedOption()!.badge }}
            </span>
          }
        </div>

        <div class="flex items-center gap-1 shrink-0 text-slate-400">
          @if (loading) {
            <svg class="w-3.5 h-3.5 animate-spin text-blue-500 shrink-0" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"></path>
            </svg>
          } @else if (allowClear && selectedOption()) {
            <span
              (click)="clearSelection($event)"
              class="p-0.5 hover:text-slate-600 dark:hover:text-slate-200 rounded cursor-pointer"
              title="Clear"
            >
              <svg class="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
              </svg>
            </span>
          }
          <svg
            class="w-4 h-4 transition-transform duration-200"
            [class.rotate-180]="isOpen()"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"/>
          </svg>
        </div>
      </button>

      <!-- Dropdown Menu -->
      @if (isOpen()) {
        <div
          class="absolute left-0 top-full mt-1.5 w-full min-w-[200px] bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-xl shadow-xl z-[100] overflow-hidden animate-in fade-in zoom-in-95 duration-150"
        >
          <!-- Search input if searchable -->
          @if (searchable) {
            <div class="p-2 border-b border-slate-100 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-900/50">
              <div class="relative">
                <svg
                  class="w-3.5 h-3.5 absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400"
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                >
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/>
                </svg>
                <input
                  #searchInput
                  type="text"
                  [ngModel]="searchQuery()"
                  (ngModelChange)="onSearchInput($event)"
                  [placeholder]="searchPlaceholder"
                  class="w-full pl-8 pr-7 py-1.5 text-xs bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg text-slate-800 dark:text-white placeholder-slate-400 focus:outline-none focus:ring-1 focus:ring-blue-500 font-medium"
                  (click)="$event.stopPropagation()"
                />
                @if (loading) {
                  <svg class="w-3.5 h-3.5 absolute right-2.5 top-1/2 -translate-y-1/2 animate-spin text-blue-500" fill="none" viewBox="0 0 24 24">
                    <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                    <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"></path>
                  </svg>
                }
              </div>
            </div>
          }

          <!-- Options List (with max-height and custom scroll) -->
          <div class="max-h-56 overflow-y-auto custom-scrollbar p-1 space-y-0.5 divide-y divide-transparent">
            @if (loading && filteredOptions().length === 0) {
              <div class="flex items-center justify-center gap-2 py-6 text-xs text-slate-400 font-medium">
                <svg class="w-4 h-4 animate-spin text-blue-500" fill="none" viewBox="0 0 24 24">
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"></path>
                </svg>
                <span>Searching...</span>
              </div>
            } @else {
              @for (opt of filteredOptions(); track (opt.value !== null && opt.value !== undefined ? opt.value : opt.label)) {
                <button
                  type="button"
                  (click)="selectOption(opt)"
                  class="w-full flex items-center justify-between gap-2 px-3 py-2 text-xs rounded-lg transition-colors text-left cursor-pointer"
                  [ngClass]="{
                    'bg-blue-50 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300 font-bold': isSelected(opt),
                    'text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800 font-medium': !isSelected(opt)
                  }"
                >
                  <div class="flex items-center gap-2 truncate">
                    @if (opt.icon) {
                      <span class="text-sm shrink-0">{{ opt.icon }}</span>
                    }
                    <div class="truncate">
                      <span class="block truncate">{{ opt.label }}</span>
                      @if (opt.sublabel) {
                        <span class="block text-[10px] text-slate-400 font-normal truncate">{{ opt.sublabel }}</span>
                      }
                    </div>
                  </div>

                  <div class="flex items-center gap-1.5 shrink-0">
                    @if (opt.badge) {
                      <span class="px-1.5 py-0.5 rounded text-[9px] font-bold bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-400">
                        {{ opt.badge }}
                      </span>
                    }
                    @if (isSelected(opt)) {
                      <svg class="w-4 h-4 text-blue-600 dark:text-blue-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M5 13l4 4L19 7"/>
                      </svg>
                    }
                  </div>
                </button>
              } @empty {
                <div class="py-4 text-center text-xs text-slate-400">
                  No matching options found
                </div>
              }
            }
          </div>
        </div>
      }
    </div>
  `
})
export class CustomSelectComponent implements ControlValueAccessor, OnInit, OnDestroy {
  private optionsSignal = signal<SelectOption[]>([]);
  @Input() set options(val: SelectOption[] | null | undefined) {
    this.optionsSignal.set(val || []);
  }
  get options(): SelectOption[] {
    return this.optionsSignal();
  }

  private selectedSignal = signal<any>(null);
  @Input() set selected(val: any) {
    this.selectedSignal.set(val);
  }
  get selected(): any {
    return this.selectedSignal();
  }

  @Input() placeholder: string = 'Select option';
  @Input() searchPlaceholder: string = 'Search...';
  @Input() allowClear: boolean = false;
  @Input() disabled: boolean = false;
  @Input() searchable: boolean = true;
  @Input() serverSearch: boolean = false;
  @Input() loading: boolean = false;

  @Output() selectedChange = new EventEmitter<any>();
  @Output() searchChange = new EventEmitter<string>();

  @ViewChild('searchInput') searchInputField?: ElementRef<HTMLInputElement>;

  isOpen = signal<boolean>(false);
  searchQuery = signal<string>('');

  private searchSubject = new Subject<string>();
  private searchSub?: Subscription;

  private onChange: (val: any) => void = () => {};
  private onTouched: () => void = () => {};

  constructor(private elementRef: ElementRef) {}

  ngOnInit() {
    this.searchSub = this.searchSubject.pipe(
      debounceTime(250),
      distinctUntilChanged()
    ).subscribe(query => {
      this.searchChange.emit(query);
    });
  }

  ngOnDestroy() {
    this.searchSub?.unsubscribe();
  }

  // ControlValueAccessor implementation
  writeValue(val: any): void {
    this.selectedSignal.set(val);
  }

  registerOnChange(fn: any): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: any): void {
    this.onTouched = fn;
  }

  setDisabledState?(isDisabled: boolean): void {
    this.disabled = isDisabled;
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent) {
    if (!this.elementRef.nativeElement.contains(event.target)) {
      this.close();
    }
  }

  @HostListener('document:keydown.escape')
  onEscape() {
    this.close();
  }

  toggleOpen() {
    if (this.disabled) return;
    const next = !this.isOpen();
    this.isOpen.set(next);
    if (next) {
      this.searchQuery.set('');
      if (this.serverSearch) {
        this.searchChange.emit('');
      }
      if (this.searchable) {
        setTimeout(() => this.searchInputField?.nativeElement?.focus(), 50);
      }
    } else {
      this.onTouched();
    }
  }

  close() {
    this.isOpen.set(false);
    this.searchQuery.set('');
    this.onTouched();
  }

  onSearchInput(query: string) {
    this.searchQuery.set(query);
    if (this.serverSearch) {
      this.searchSubject.next(query);
    }
  }

  selectedOption = computed(() => {
    const cur = this.selectedSignal();
    return this.optionsSignal().find(opt => opt.value === cur) || null;
  });

  filteredOptions = computed(() => {
    const opts = this.optionsSignal();
    if (this.serverSearch) {
      return opts;
    }
    const q = this.searchQuery().trim().toLowerCase();
    if (!q) return opts;
    return opts.filter(opt =>
      (opt.label && opt.label.toLowerCase().includes(q)) ||
      (opt.sublabel && opt.sublabel.toLowerCase().includes(q)) ||
      (opt.badge && opt.badge.toLowerCase().includes(q))
    );
  });

  isSelected(option: SelectOption): boolean {
    return this.selectedSignal() === option.value;
  }

  selectOption(option: SelectOption) {
    this.selectedSignal.set(option.value);
    this.selectedChange.emit(option.value);
    this.onChange(option.value);
    this.onTouched();
    this.close();
  }

  clearSelection(event: MouseEvent) {
    event.stopPropagation();
    this.selectedSignal.set(null);
    this.selectedChange.emit(null);
    this.onChange(null);
    this.onTouched();
  }
}
