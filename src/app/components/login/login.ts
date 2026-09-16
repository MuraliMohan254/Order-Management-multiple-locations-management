import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { AuthService } from '../../services/auth.service';
import { SharedService } from '../../services/shared';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: './login.html'
})
export class LoginComponent {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  public sharedService = inject(SharedService);
  private router = inject(Router);
  private toastr = inject(ToastrService);
  
  isLoading = signal(false);
  showPassword = signal(false);
  errorMessage = signal<string | null>(null);

  loginForm = this.fb.nonNullable.group({
    username: ['', [Validators.required, Validators.minLength(3)]],
    password: ['', [Validators.required, Validators.minLength(5)]]
  });

  togglePasswordVisibility() {
    this.showPassword.update(show => !show);
  }

  onSubmit() {
    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);
    const { username, password } = this.loginForm.getRawValue();

    // Connecting to actual backend auth service configured earlier
    this.authService.login({ username, password }).subscribe({
      next: () => {
        this.toastr.success(`Welcome back to ${this.sharedService.appName}`, 'Login Successful');
        this.router.navigate(['/dashboard']);
      },
      error: (err) => {
        this.isLoading.set(false);
        const errorMsg = err.error?.message || 'Invalid username or password';
        this.errorMessage.set(errorMsg);
        this.toastr.error(errorMsg, 'Authentication Failed');
      }
    });
  }
}