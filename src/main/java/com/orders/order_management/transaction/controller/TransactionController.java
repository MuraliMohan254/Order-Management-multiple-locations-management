package com.orders.order_management.transaction.controller;


import com.orders.order_management.core.dto.PagedResponse;
import com.orders.order_management.core.enums.OrderStatus;
import com.orders.order_management.core.enums.OrderType;
import com.orders.order_management.core.enums.PaymentMethod;
import com.orders.order_management.transaction.dto.KitchenTicketResponse;
import com.orders.order_management.transaction.dto.OrderItemRequest;
import com.orders.order_management.transaction.dto.OrderResponse;
import com.orders.order_management.transaction.dto.PaymentResponse;
import com.orders.order_management.transaction.dto.TableResponse;
import com.orders.order_management.transaction.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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

    @GetMapping("/public/table/{qrToken}")
    public ResponseEntity<TableResponse> getTableByQrToken(@PathVariable UUID qrToken) {
        return ResponseEntity.ok(transactionService.getTableByQrToken(qrToken));
    }

    @GetMapping("/public/table/{qrToken}/active-order")
    public ResponseEntity<OrderResponse> getActiveOrderByQrToken(@PathVariable UUID qrToken) {
        OrderResponse activeOrder = transactionService.getActiveOrderByQrToken(qrToken);
        return activeOrder != null ? ResponseEntity.ok(activeOrder) : ResponseEntity.noContent().build();
    }

    @GetMapping("/public/orders/{orderId}")
    public ResponseEntity<OrderResponse> getPublicOrderById(@PathVariable UUID orderId) {
        return ResponseEntity.ok(transactionService.getOrderById(orderId));
    }

    @GetMapping("/public/config")
    public ResponseEntity<java.util.Map<String, Object>> getAppConfig() {
        return ResponseEntity.ok(transactionService.getAppConfig());
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
            @RequestParam(required = false) OrderStatus status,
            @RequestBody(required = false) Map<String, String> body) {
        OrderStatus effectiveStatus = status;
        if (effectiveStatus == null && body != null && body.containsKey("status")) {
            try {
                effectiveStatus = OrderStatus.valueOf(body.get("status").trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }
        if (effectiveStatus == null) {
            throw new IllegalArgumentException("Status is required via query param or JSON body");
        }
        return ResponseEntity.ok(transactionService.updateOrderStatus(orderId, effectiveStatus));
    }

    @GetMapping("/orders/kitchen/active")
    public ResponseEntity<List<KitchenTicketResponse>> getActiveKitchenTickets() {
        return ResponseEntity.ok(transactionService.getActiveKitchenTickets());
    }

    @PostMapping("/orders/{targetOrderId}/merge")
    public ResponseEntity<OrderResponse> mergeOrders(
            @PathVariable UUID targetOrderId,
            @RequestParam UUID sourceOrderId) {
        return ResponseEntity.ok(transactionService.mergeOrders(targetOrderId, sourceOrderId));
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

    @GetMapping("/orders/paged")
    public ResponseEntity<PagedResponse<OrderResponse>> getPagedOrders(
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(required = false) String orderType,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {

        List<OrderStatus> statusEnums = null;
        if (statuses != null && !statuses.isEmpty()) {
            statusEnums = statuses.stream()
                    .filter(s -> s != null && !s.isBlank())
                    .map(s -> {
                        try { return OrderStatus.valueOf(s.trim().toUpperCase()); }
                        catch (IllegalArgumentException e) { return null; }
                    })
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }

        OrderType orderTypeEnum = null;
        if (orderType != null && !orderType.isBlank()) {
            try {
                orderTypeEnum = OrderType.valueOf(orderType.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        return ResponseEntity.ok(transactionService.getPagedOrders(statusEnums, orderTypeEnum, search, page, size, sortBy, sortDirection));
    }
}
