import { Injectable, inject } from '@angular/core';
import Swal, { SweetAlertIcon, SweetAlertResult } from 'sweetalert2';
import { ThemeService } from './theme.service';

export interface ConfirmDialogOptions {
  title?: string;
  text?: string;
  confirmButtonText?: string;
  cancelButtonText?: string;
  icon?: SweetAlertIcon;
  isDestructive?: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class AlertService {
  private themeService = inject(ThemeService);

  private get isDark(): boolean {
    return this.themeService.isDark();
  }

  private getThemeOptions() {
    const dark = this.isDark;
    return {
      background: dark ? '#1e293b' : '#ffffff',
      color: dark ? '#f8fafc' : '#0f172a',
      confirmColor: '#059669', // Emerald 600
      dangerColor: '#e11d48',  // Rose 600
      cancelColor: dark ? '#334155' : '#94a3b8', // Slate 700 / Slate 400
    };
  }

  /**
   * Non-blocking Toast notification positioned at bottom-right
   */
  private showToast(icon: SweetAlertIcon, message: string, title?: string): Promise<SweetAlertResult> {
    const theme = this.getThemeOptions();

    const Toast = Swal.mixin({
      toast: true,
      position: 'bottom-right',
      showConfirmButton: false,
      timer: 3500,
      timerProgressBar: true,
      background: theme.background,
      color: theme.color,
      customClass: {
        popup: `wildbar-toast wildbar-toast-${icon}`,
        title: 'wildbar-toast-title',
        htmlContainer: 'wildbar-toast-html'
      },
      didOpen: (toast) => {
        toast.onmouseenter = Swal.stopTimer;
        toast.onmouseleave = Swal.resumeTimer;
      }
    });

    if (title) {
      return Toast.fire({
        icon,
        title,
        text: message
      });
    }

    return Toast.fire({
      icon,
      title: message
    });
  }

  /**
   * Success notification toast
   */
  success(message: string, title?: string): Promise<SweetAlertResult> {
    return this.showToast('success', message, title);
  }

  /**
   * Error notification toast
   */
  error(message: string, title?: string): Promise<SweetAlertResult> {
    return this.showToast('error', message, title);
  }

  /**
   * Warning notification toast
   */
  warning(message: string, title?: string): Promise<SweetAlertResult> {
    return this.showToast('warning', message, title);
  }

  /**
   * Info notification toast
   */
  info(message: string, title?: string): Promise<SweetAlertResult> {
    return this.showToast('info', message, title);
  }

  /**
   * Standard modal alert with OK button
   */
  alert(title: string, text?: string, icon: SweetAlertIcon = 'info'): Promise<SweetAlertResult> {
    const theme = this.getThemeOptions();
    return Swal.fire({
      title,
      text,
      icon,
      background: theme.background,
      color: theme.color,
      confirmButtonColor: theme.confirmColor,
      customClass: {
        popup: 'wildbar-modal-popup',
        confirmButton: 'wildbar-btn-confirm'
      }
    });
  }

  /**
   * Modal confirmation dialog returning Promise<boolean>
   * Replaces native window.confirm() with beautiful, theme-aware SweetAlert2 dialogs
   */
  async confirm(
    messageOrOptions: string | ConfirmDialogOptions,
    defaultTitle = 'Are you sure?'
  ): Promise<boolean> {
    const theme = this.getThemeOptions();
    let opts: ConfirmDialogOptions;

    if (typeof messageOrOptions === 'string') {
      opts = {
        title: defaultTitle,
        text: messageOrOptions,
        confirmButtonText: 'Yes, proceed',
        cancelButtonText: 'Cancel',
        icon: 'warning',
        isDestructive: false
      };
    } else {
      opts = {
        title: messageOrOptions.title || defaultTitle,
        text: messageOrOptions.text,
        confirmButtonText: messageOrOptions.confirmButtonText || 'Yes, proceed',
        cancelButtonText: messageOrOptions.cancelButtonText || 'Cancel',
        icon: messageOrOptions.icon || 'warning',
        isDestructive: messageOrOptions.isDestructive ?? true
      };
    }

    const result = await Swal.fire({
      title: opts.title,
      text: opts.text,
      icon: opts.icon,
      showCancelButton: true,
      confirmButtonText: opts.confirmButtonText,
      cancelButtonText: opts.cancelButtonText,
      confirmButtonColor: opts.isDestructive ? theme.dangerColor : theme.confirmColor,
      cancelButtonColor: theme.cancelColor,
      background: theme.background,
      color: theme.color,
      reverseButtons: true,
      focusCancel: true,
      customClass: {
        popup: 'wildbar-modal-popup',
        confirmButton: 'wildbar-btn-confirm',
        cancelButton: 'wildbar-btn-cancel',
        actions: 'wildbar-actions'
      }
    });

    return !!result.isConfirmed;
  }
}
