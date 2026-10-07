import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ToastrService } from 'ngx-toastr';
import { BillingService } from '../../services/billing/billing.service';
import { OrderResponse } from '../../models/pos/pos.models';

@Component({
  selector: 'app-orders-billing',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './orders-billing.html'
})
export class OrdersBillingComponent implements OnInit {
  private billingService = inject(BillingService);
  private toastr = inject(ToastrService);

  orders = signal<OrderResponse[]>([]);
  selectedOrder = signal<OrderResponse | null>(null);
  
  // UI Filter State
  currentFilter = signal<'ACTIVE' | 'COMPLETED' | 'CANCELLED'>('ACTIVE');
  
  isLoading = signal(false);
  isProcessing = signal(false);
  
  discountInput = signal<number>(0);
  paymentMethod = signal<'CASH' | 'CARD' | 'UPI'>('CASH');

  ngOnInit() {
    this.loadOrders();
  }

  setFilter(filter: 'ACTIVE' | 'COMPLETED' | 'CANCELLED') {
    this.currentFilter.set(filter);
    this.selectedOrder.set(null); // Clear selection on filter change
    this.loadOrders();
  }

  loadOrders() {
    this.isLoading.set(true);
    
    // Map UI filter to Backend Enum Array
    let queryStatuses: string[] = [];
    if (this.currentFilter() === 'ACTIVE') {
      queryStatuses = ['SOFT_ORDER', 'CONFIRMED', 'BILLED'];
    } else if (this.currentFilter() === 'COMPLETED') {
      queryStatuses = ['COMPLETED']; // Assuming you have a COMPLETED status post-payment
    } else if (this.currentFilter() === 'CANCELLED') {
      queryStatuses = ['CANCELLED'];
    }

    this.billingService.getOrders(queryStatuses).subscribe({
      next: (data) => {
        this.orders.set(data);
        if (this.selectedOrder()) {
          const updated = data.find(o => o.id === this.selectedOrder()!.id);
          this.selectedOrder.set(updated || null);
        }
        this.isLoading.set(false);
      },
      error: () => {
        this.toastr.error('Failed to load orders');
        this.isLoading.set(false);
      }
    });
  }

  selectOrder(orderId: string) {
    this.billingService.getOrderById(orderId).subscribe({
      next: (order) => {
        this.selectedOrder.set(order);
        this.discountInput.set(0); 
      },
      error: () => this.toastr.error('Failed to fetch order details')
    });
  }

  generateBill() {
    const order = this.selectedOrder();
    if (!order) return;
    
    this.isProcessing.set(true);
    this.billingService.generateBill(order.id, this.discountInput()).subscribe({
      next: () => {
        this.toastr.success('Bill generated successfully');
        this.loadOrders(); 
        this.isProcessing.set(false);
      },
      error: () => {
        this.toastr.error('Failed to generate bill');
        this.isProcessing.set(false);
      }
    });
  }

  settlePayment() {
    const order = this.selectedOrder();
    if (!order || order.status !== 'BILLED') return;

    this.isProcessing.set(true);
    this.billingService.settlePayment(order.id, this.paymentMethod(), order.grandTotal).subscribe({
      next: () => {
        this.toastr.success(`Payment successful!`);
        this.selectedOrder.set(null);
        this.loadOrders(); // Auto-refreshes to remove the paid order from the ACTIVE tab
        this.isProcessing.set(false);
      },
      error: () => {
        this.toastr.error('Payment failed');
        this.isProcessing.set(false);
      }
    });
  }
}