package com.orders.order_management.service;

import com.orders.order_management.dto.*;
import com.orders.order_management.entity.*;
import com.orders.order_management.enums.*;
import com.orders.order_management.repos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class TransactionService {

    private final RestaurantTableRepository tableRepository;
    private final OrderRepository orderRepository;
    private final ItemRepository itemRepository;
    private final PaymentRepository paymentRepository;
    private final OrderItemRepository orderItemRepository;

    public TableResponse createTable(String tableNumber) {
        RestaurantTable table = RestaurantTable.builder()
                .tableNumber(tableNumber)
                .qrToken(UUID.randomUUID())
                .status(TableStatus.AVAILABLE)
                .build();
        return mapToTableResponse(tableRepository.save(table));
    }

    public OrderResponse processQrOrder(UUID qrToken, List<OrderItemRequest> itemRequests) {
        RestaurantTable table = tableRepository.findByQrToken(qrToken)
                .orElseThrow(() -> new IllegalArgumentException("Invalid QR Token"));

        Order order = orderRepository.findByTableIdAndStatusIn(
                table.getId(), List.of(OrderStatus.SOFT_ORDER, OrderStatus.CONFIRMED)).orElse(null);

        if (order == null) {
            order = Order.builder()
                    .orderNumber("ORD-" + System.currentTimeMillis())
                    .orderType(OrderType.DINE_IN)
                    .status(OrderStatus.SOFT_ORDER)
                    .table(table)
                    .build();
            table.setStatus(TableStatus.OCCUPIED);
            tableRepository.save(table);
        }

        appendItemsToOrder(order, itemRequests);
        return mapToOrderResponse(orderRepository.save(order));
    }

    public OrderResponse appendItemsToOrder(UUID orderId, List<OrderItemRequest> requests) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        if (order.getStatus() == OrderStatus.BILLED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Cannot modify a finalized order.");
        }

        appendItemsToOrder(order, requests);
        return mapToOrderResponse(orderRepository.save(order));
    }

    private void appendItemsToOrder(Order order, List<OrderItemRequest> requests) {
        BigDecimal runningSubTotal = order.getSubTotal();

        for (OrderItemRequest req : requests) {
            Item item = itemRepository.findById(req.getItemId())
                    .orElseThrow(() -> new IllegalArgumentException("Item not found"));

            BigDecimal lineTotal = item.getPrice().multiply(new BigDecimal(req.getQuantity()));
            runningSubTotal = runningSubTotal.add(lineTotal);

            OrderItem orderItem = OrderItem.builder()
                    .item(item)
                    .quantity(req.getQuantity())
                    .unitPrice(item.getPrice())
                    .totalPrice(lineTotal)
                    .notes(req.getNotes())
                    .build();

            order.addOrderItem(orderItem);
        }
        order.setSubTotal(runningSubTotal);
    }

    public OrderResponse generateFinalBill(UUID orderId, BigDecimal discount) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        BigDecimal taxRate = new BigDecimal("0.05"); // 5% configurable tax
        BigDecimal taxAmount = order.getSubTotal().multiply(taxRate);
        BigDecimal finalDiscount = discount != null ? discount : BigDecimal.ZERO;

        BigDecimal grandTotal = order.getSubTotal()
                .add(taxAmount)
                .subtract(finalDiscount);

        order.setTaxAmount(taxAmount);
        order.setDiscountAmount(finalDiscount);
        order.setGrandTotal(grandTotal);
        order.setStatus(OrderStatus.BILLED);

        return mapToOrderResponse(orderRepository.save(order));
    }

    public PaymentResponse settlePayment(UUID orderId, PaymentMethod method, BigDecimal amount) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        Payment payment = Payment.builder()
                .order(order)
                .amountPaid(amount)
                .paymentMethod(method)
                .paymentStatus(PaymentStatus.SUCCESS)
                .build();

        paymentRepository.save(payment);

        if (amount.compareTo(order.getGrandTotal()) >= 0 && order.getTable() != null) {
            RestaurantTable table = order.getTable();
            table.setStatus(TableStatus.AVAILABLE);
            tableRepository.save(table);
        }

        return new PaymentResponse(payment.getId(), orderId, amount, method, PaymentStatus.SUCCESS);
    }

    private TableResponse mapToTableResponse(RestaurantTable table) {
        return new TableResponse(table.getId(), table.getTableNumber(), table.getQrToken(), table.getStatus().name());
    }

    private OrderResponse mapToOrderResponse(Order order) {
        return new OrderResponse(
                order.getId(), order.getOrderNumber(), order.getOrderType().name(),
                order.getStatus().name(), order.getSubTotal(), order.getTaxAmount(),
                order.getDiscountAmount(), order.getGrandTotal()
        );
    }

    public OrderResponse createTakeAwayOrder(List<OrderItemRequest> itemRequests) {
        Order order = Order.builder()
                .orderNumber("TA-" + System.currentTimeMillis())
                .orderType(OrderType.TAKE_AWAY)
                .status(OrderStatus.CONFIRMED) // Take-aways usually go straight to kitchen
                .table(null) // No table required
                .build();

        order = orderRepository.save(order);
        appendItemsToOrder(order, itemRequests);

        return mapToOrderResponse(orderRepository.save(order));
    }

    public OrderResponse updateOrderStatus(UUID orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        if (order.getStatus() == OrderStatus.BILLED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Cannot change status of a finalized order.");
        }

        order.setStatus(newStatus);
        return mapToOrderResponse(orderRepository.save(order));
    }

    // --- 2. CANCEL ENTIRE ORDER ---
    public OrderResponse cancelOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        if (order.getStatus() == OrderStatus.BILLED) {
            throw new IllegalStateException("Order is already billed. Issue a refund instead.");
        }

        order.setStatus(OrderStatus.CANCELLED);

        // Free up the table immediately
        if (order.getTable() != null) {
            RestaurantTable table = order.getTable();
            table.setStatus(TableStatus.AVAILABLE);
            tableRepository.save(table);
        }

        return mapToOrderResponse(orderRepository.save(order));
    }

    // --- 3. VOID/REMOVE SPECIFIC ITEM (Return before billing) ---
    public OrderResponse voidOrderItem(UUID orderId, UUID orderItemId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        if (order.getStatus() == OrderStatus.BILLED) {
            throw new IllegalStateException("Cannot void items from a billed order.");
        }

        OrderItem itemToRemove = order.getItems().stream()
                .filter(item -> item.getId().equals(orderItemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Item not found in this order"));

        // Remove item and let JPA orphanRemoval delete it from the database
        order.getItems().remove(itemToRemove);

        // Recalculate subtotal
        BigDecimal updatedSubTotal = order.getSubTotal().subtract(itemToRemove.getTotalPrice());
        order.setSubTotal(updatedSubTotal.max(BigDecimal.ZERO)); // Prevent negative subtotals

        return mapToOrderResponse(orderRepository.save(order));
    }

    public List<KitchenTicketResponse> getActiveKitchenTickets() {
        return orderItemRepository.findActiveKitchenTickets().stream()
                .map(oi -> KitchenTicketResponse.builder()
                        .orderItemId(oi.getId())
                        .orderNumber(oi.getOrder().getOrderNumber())
                        .tableName(oi.getOrder().getTable() != null ? oi.getOrder().getTable().getTableNumber() : "Take-Away")
                        .itemName(oi.getItem().getName())
                        .quantity(oi.getQuantity())
                        .notes(oi.getNotes())
                        .orderTime(oi.getCreatedAt())
                        .build())
                .toList();
    }
}
