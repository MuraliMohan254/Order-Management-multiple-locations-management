package com.orders.order_management.controller;

import com.orders.order_management.dto.*;
import com.orders.order_management.enums.OrderStatus;
import com.orders.order_management.enums.PaymentMethod;
import com.orders.order_management.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("${api.prefix}")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/tables")
    public ResponseEntity<TableResponse> createTable(@RequestParam String tableNumber) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transactionService.createTable(tableNumber));
    }

    @PostMapping("/public/qr-order/{qrToken}")
    public ResponseEntity<OrderResponse> processQrOrder(
            @PathVariable UUID qrToken,
            @Valid @RequestBody List<OrderItemRequest> items) {
        return ResponseEntity.ok(transactionService.processQrOrder(qrToken, items));
    }

    @PostMapping("/orders/{orderId}/items")
    public ResponseEntity<OrderResponse> addItemsToOrder(
            @PathVariable UUID orderId,
            @Valid @RequestBody List<OrderItemRequest> items) {
        return ResponseEntity.ok(transactionService.appendItemsToOrder(orderId, items));
    }

    @PostMapping("/orders/{orderId}/bill")
    public ResponseEntity<OrderResponse> generateBill(
            @PathVariable UUID orderId,
            @RequestParam(required = false) BigDecimal discount) {
        return ResponseEntity.ok(transactionService.generateFinalBill(orderId, discount));
    }

    @PostMapping("/orders/{orderId}/pay")
    public ResponseEntity<PaymentResponse> settlePayment(
            @PathVariable UUID orderId,
            @RequestParam PaymentMethod method,
            @RequestParam BigDecimal amount) {
        return ResponseEntity.ok(transactionService.settlePayment(orderId, method, amount));
    }

    @PostMapping("/orders/take-away")
    public ResponseEntity<OrderResponse> createTakeAwayOrder(
            @Valid @RequestBody List<OrderItemRequest> items) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transactionService.createTakeAwayOrder(items));
    }

    // ==========================================
    // WORKFLOW & KITCHEN ENDPOINTS
    // ==========================================

    @PutMapping("/orders/{orderId}/status")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @PathVariable UUID orderId,
            @RequestParam OrderStatus status) {
        return ResponseEntity.ok(transactionService.updateOrderStatus(orderId, status));
    }

    @GetMapping("/orders/kitchen/active")
    public ResponseEntity<List<KitchenTicketResponse>> getActiveKitchenTickets() {
        return ResponseEntity.ok(transactionService.getActiveKitchenTickets());
    }

    // ==========================================
    // CANCELLATION & VOID ENDPOINTS
    // ==========================================

    @PutMapping("/orders/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(transactionService.cancelOrder(orderId));
    }

    @DeleteMapping("/orders/{orderId}/items/{orderItemId}")
    public ResponseEntity<OrderResponse> voidOrderItem(
            @PathVariable UUID orderId,
            @PathVariable UUID orderItemId) {
        return ResponseEntity.ok(transactionService.voidOrderItem(orderId, orderItemId));
    }

    // ==========================================
    // TABLE & POS DINE-IN ENDPOINTS
    // ==========================================

    @GetMapping("/tables")
    public ResponseEntity<List<TableResponse>> getTables() {
        return ResponseEntity.ok(transactionService.getAllTables());
    }

    @PostMapping("/orders/dine-in")
    public ResponseEntity<OrderResponse> createPosDineInOrder(
            @RequestParam UUID tableId,
            @Valid @RequestBody List<OrderItemRequest> items) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.createPosDineInOrder(tableId, items));
    }
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable UUID orderId) {
        return ResponseEntity.ok(transactionService.getOrderById(orderId));
    }

    @GetMapping("/orders")
    public ResponseEntity<List<OrderResponse>> getOrders(
            @RequestParam(required = false) List<OrderStatus> statuses) {
        return ResponseEntity.ok(transactionService.getOrders(statuses));
    }
}
