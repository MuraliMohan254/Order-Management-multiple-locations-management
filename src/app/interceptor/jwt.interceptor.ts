import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError } from 'rxjs/operators';
import { throwError } from 'rxjs';
import { AlertService } from '../services/alert.service';
import { Router } from '@angular/router';

export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  const token = sessionStorage.getItem('jwt_token');
  const isLoginRequest = req.url.includes('/login');
  
  const alertService = inject(AlertService);
  const router = inject(Router);

  let modifiedReq = req;

  if (token && !isLoginRequest) {
    modifiedReq = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
  }
  
  return next(modifiedReq).pipe(
    catchError((error: HttpErrorResponse) => {
      // Don't intercept 401/403 for the login endpoint itself, because login handles it directly
      if (!isLoginRequest) {
        if (error.status === 403) {
          alertService.error('You do not have permission to access this resource.', 'Access Denied');
        } else if (error.status === 401) {
          alertService.error('Your session has expired. Please log in again.', 'Unauthorized');
          sessionStorage.removeItem('jwt_token');
          router.navigate(['/login']);
        }
      }
      return throwError(() => error);
    })
  );
};
