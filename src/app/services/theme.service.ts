import { Injectable, signal, effect, computed } from '@angular/core';

export type AppTheme = 'light' | 'dark' | 'system';

@Injectable({
  providedIn: 'root'
})
export class ThemeService {
  private readonly THEME_KEY = 'wildbar_theme';

  // Current selected preference
  theme = signal<AppTheme>(this.getInitialTheme());

  // Signal tracking system OS dark preference
  private systemPrefersDark = signal<boolean>(this.checkSystemDark());

  // Effective dark mode boolean (whether dark styling should be active)
  isDark = computed(() => {
    const current = this.theme();
    if (current === 'dark') return true;
    if (current === 'light') return false;
    return this.systemPrefersDark();
  });

  constructor() {
    // Listen for OS scheme changes if user chooses 'system'
    if (typeof window !== 'undefined' && window.matchMedia) {
      const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
      mediaQuery.addEventListener('change', (e) => {
        this.systemPrefersDark.set(e.matches);
      });
    }

    // Effect to apply classes and update document root
    effect(() => {
      const dark = this.isDark();
      this.applyTheme(dark);
    });
  }

  private getInitialTheme(): AppTheme {
    if (typeof localStorage === 'undefined') return 'light';
    const stored = localStorage.getItem(this.THEME_KEY) as AppTheme | null;
    if (stored === 'dark' || stored === 'light' || stored === 'system') {
      return stored;
    }
    return 'light'; // Default to light theme for hospitality POS
  }

  private checkSystemDark(): boolean {
    if (typeof window === 'undefined' || !window.matchMedia) return false;
    return window.matchMedia('(prefers-color-scheme: dark)').matches;
  }

  private applyTheme(dark: boolean): void {
    if (typeof document === 'undefined') return;
    const root = document.documentElement;
    if (dark) {
      root.classList.add('dark');
      root.setAttribute('data-theme', 'dark');
      root.style.colorScheme = 'dark';
    } else {
      root.classList.remove('dark');
      root.setAttribute('data-theme', 'light');
      root.style.colorScheme = 'light';
    }
  }

  setTheme(newTheme: AppTheme): void {
    this.theme.set(newTheme);
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem(this.THEME_KEY, newTheme);
    }
  }

  toggleTheme(): void {
    const next = this.isDark() ? 'light' : 'dark';
    this.setTheme(next);
  }
}
