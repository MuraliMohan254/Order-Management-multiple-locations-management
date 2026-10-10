package com.orders.order_management.transaction.service;


import com.orders.order_management.core.dto.PagedResponse;
import com.orders.order_management.core.enums.OrderStatus;
import com.orders.order_management.core.enums.OrderType;
import com.orders.order_management.core.enums.PaymentMethod;
import com.orders.order_management.core.enums.PaymentStatus;
import com.orders.order_management.core.enums.TableStatus;
import com.orders.order_management.menu.entity.Item;
import com.orders.order_management.menu.repository.ItemRepository;
import com.orders.order_management.transaction.dto.KitchenTicketResponse;
import com.orders.order_management.transaction.dto.OrderItemDetailResponse;
import com.orders.order_management.transaction.dto.OrderItemRequest;
import com.orders.order_management.transaction.dto.OrderResponse;
import com.orders.order_management.transaction.dto.PaymentResponse;
import com.orders.order_management.transaction.dto.TableResponse;
import com.orders.order_management.transaction.entity.Order;
import com.orders.order_management.transaction.entity.OrderItem;
import com.orders.order_management.transaction.entity.Payment;
import com.orders.order_management.transaction.entity.RestaurantTable;
import com.orders.order_management.transaction.repository.OrderItemRepository;
import com.orders.order_management.transaction.repository.OrderRepository;
import com.orders.order_management.transaction.repository.OrderSpecification;
import com.orders.order_management.transaction.repository.PaymentRepository;
import com.orders.order_management.transaction.repository.RestaurantTableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
    private final com.orders.order_management.core.service.SystemConfigService configService;

    private String determineInitialKitchenStatus() {
        if (!configService.getConfig().isKitchenWorkflowEnabled()) {
            return "SERVED"; // Bypass kitchen KDS
        }
        return com.orders.order_management.core.enums.OrderStatus.CONFIRMED.name();
    }

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
                table.getId(), List.of(OrderStatus.SOFT_ORDER, OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.READY, OrderStatus.SERVED)).orElse(null);

        if (order == null) {
            order = Order.builder()
                    .orderNumber("ORD-" + System.currentTimeMillis() + "-" + java.util.UUID.randomUUID().toString().substring(0,4).toUpperCase())
                    .orderType(OrderType.DINE_IN)
                    .status(OrderStatus.CONFIRMED)
                    .kitchenStatus(determineInitialKitchenStatus())
                    .table(table)
                    .subTotal(BigDecimal.ZERO)
                    .build();
            table.setStatus(TableStatus.OCCUPIED);
            tableRepository.save(table);
            order = orderRepository.save(order);
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
        BigDecimal effectiveTaxRate = configService.getConfig().getTaxRate();
        order.setTaxAmount(order.getSubTotal().multiply(effectiveTaxRate).setScale(2, java.math.RoundingMode.HALF_UP));
        BigDecimal discount = order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO;
        order.setGrandTotal(order.getSubTotal().add(order.getTaxAmount()).subtract(discount));

        // If order was already completed or served, reset back to CONFIRMED so newly appended items route to the kitchen
        if (order.getStatus() == OrderStatus.SERVED || order.getStatus() == OrderStatus.READY) {
            order.setStatus(OrderStatus.CONFIRMED);
            order.setKitchenStatus(determineInitialKitchenStatus());
        }
    }

    public OrderResponse generateFinalBill(UUID orderId, BigDecimal discount) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        BigDecimal effectiveTaxRate = configService.getConfig().getTaxRate();
        BigDecimal taxAmount = order.getSubTotal().multiply(effectiveTaxRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal finalDiscount = discount != null ? discount : BigDecimal.ZERO;

        BigDecimal grandTotal = order.getSubTotal()
                .add(taxAmount)
                .subtract(finalDiscount);

        order.setTaxAmount(taxAmount);
        order.setDiscountAmount(finalDiscount);
        order.setGrandTotal(grandTotal);
        // Ensure kitchenStatus is set so KDS knows the kitchen preparation phase
        if (order.getKitchenStatus() == null || "PENDING".equalsIgnoreCase(order.getKitchenStatus())) {
            order.setKitchenStatus(order.getStatus() != null && order.getStatus() != OrderStatus.SOFT_ORDER ? order.getStatus().name() : determineInitialKitchenStatus());
        }
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
        
        // Update order status to PAID once settled
        order.setStatus(com.orders.order_management.core.enums.OrderStatus.PAID);
        orderRepository.save(order);


        return new PaymentResponse(payment.getId(), orderId, amount, method, PaymentStatus.SUCCESS);
    }

    private TableResponse mapToTableResponse(RestaurantTable table) {
        return new TableResponse(table.getId(), table.getTableNumber(), table.getQrToken(), table.getStatus().name());
    }

    private OrderResponse mapToOrderResponse(Order order) {
        List<OrderItemDetailResponse> itemResponses = order.getItems() != null
                ? order.getItems().stream().map(oi -> OrderItemDetailResponse.builder()
                        .id(oi.getId())
                        .itemId(oi.getItem() != null ? oi.getItem().getId() : null)
                        .itemName(oi.getItem() != null ? oi.getItem().getName() : "Unknown Item")
                        .quantity(oi.getQuantity())
                        .unitPrice(oi.getUnitPrice())
                        .totalPrice(oi.getTotalPrice())
                        .notes(oi.getNotes())
                        .build()).toList()
                : Collections.emptyList();

        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .orderType(order.getOrderType() != null ? order.getOrderType().name() : null)
                .status(order.getStatus() != null ? order.getStatus().name() : null)
                .kitchenStatus(order.getKitchenStatus() != null ? order.getKitchenStatus() : (order.getStatus() != null ? order.getStatus().name() : "PENDING"))
                .tableId(order.getTable() != null ? order.getTable().getId() : null)
                .tableNumber(order.getTable() != null ? order.getTable().getTableNumber() : null)
                .subTotal(order.getSubTotal())
                .taxAmount(order.getTaxAmount())
                .discountAmount(order.getDiscountAmount())
                .grandTotal(order.getGrandTotal())
                .createdAt(order.getCreatedAt())
                .items(itemResponses)
                .build();
    }

    public OrderResponse createTakeAwayOrder(List<OrderItemRequest> itemRequests) {
        Order order = Order.builder()
                .orderNumber("TA-" + System.currentTimeMillis() + "-" + java.util.UUID.randomUUID().toString().substring(0,4).toUpperCase())
                .orderType(OrderType.TAKE_AWAY)
                .status(OrderStatus.CONFIRMED) // Take-aways usually go straight to kitchen
                .kitchenStatus(determineInitialKitchenStatus())
                .table(null) // No table required
                .subTotal(BigDecimal.ZERO)
                .build();

        order = orderRepository.save(order);
        appendItemsToOrder(order, itemRequests);

        return mapToOrderResponse(orderRepository.save(order));
    }

    public OrderResponse updateOrderStatus(UUID orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Cannot change status of a cancelled order.");
        }

        // If the order is already BILLED, kitchen operations (PREPARING, READY, SERVED) update kitchenStatus
        if (order.getStatus() == OrderStatus.BILLED) {
            order.setKitchenStatus(newStatus.name());
            return mapToOrderResponse(orderRepository.save(order));
        }

        order.setStatus(newStatus);
        order.setKitchenStatus(newStatus.name());
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
        BigDecimal effectiveTaxRate = configService.getConfig().getTaxRate();
        order.setTaxAmount(order.getSubTotal().multiply(effectiveTaxRate).setScale(2, java.math.RoundingMode.HALF_UP));
        BigDecimal discount = order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO;
        order.setGrandTotal(order.getSubTotal().add(order.getTaxAmount()).subtract(discount));

        if (order.getItems().isEmpty()) {
            order.setStatus(OrderStatus.CANCELLED);
            order.setKitchenStatus(OrderStatus.CANCELLED.name());
            if (order.getTable() != null) {
                RestaurantTable table = order.getTable();
                table.setStatus(TableStatus.AVAILABLE);
                tableRepository.save(table);
            }
        }

        return mapToOrderResponse(orderRepository.save(order));
    }

    public void clearKitchenQueue() {
        List<Order> activeOrders = orderRepository.findActiveOrdersByStatusIn(List.of(
                com.orders.order_management.core.enums.OrderStatus.CONFIRMED,
                com.orders.order_management.core.enums.OrderStatus.PREPARING,
                com.orders.order_management.core.enums.OrderStatus.READY,
                com.orders.order_management.core.enums.OrderStatus.BILLED
        ));
        for (Order order : activeOrders) {
            order.setKitchenStatus("SERVED");
        }
        orderRepository.saveAll(activeOrders);
    }

    public List<KitchenTicketResponse> getActiveKitchenTickets() {
        return orderItemRepository.findActiveKitchenTickets().stream()
                .map(oi -> KitchenTicketResponse.builder()
                        .orderItemId(oi.getId())
                        .orderId(oi.getOrder() != null ? oi.getOrder().getId() : null)
                        .orderNumber(oi.getOrder() != null ? oi.getOrder().getOrderNumber() : "")
                        .tableName(oi.getOrder() != null && oi.getOrder().getTable() != null ? oi.getOrder().getTable().getTableNumber() : "Take-Away")
                        .itemName(oi.getItem() != null ? oi.getItem().getName() : "")
                        .quantity(oi.getQuantity())
                        .notes(oi.getNotes())
                        .orderTime(oi.getCreatedAt())
                        .status(oi.getOrder() != null && oi.getOrder().getKitchenStatus() != null ? oi.getOrder().getKitchenStatus() : (oi.getOrder() != null && oi.getOrder().getStatus() != null ? oi.getOrder().getStatus().name() : ""))
                        .build())
                .toList();
    }
    // --- 1. Fetch all tables for the POS Grid ---
    public List<TableResponse> getAllTables() {
        return tableRepository.findAll().stream()
                .map(this::mapToTableResponse)
                .toList();
    }

    public TableResponse getTableByQrToken(UUID qrToken) {
        RestaurantTable table = tableRepository.findByQrToken(qrToken)
                .orElseThrow(() -> new IllegalArgumentException("Invalid QR Token"));
        return mapToTableResponse(table);
    }

    public OrderResponse getActiveOrderByQrToken(UUID qrToken) {
        RestaurantTable table = tableRepository.findByQrToken(qrToken)
                .orElseThrow(() -> new IllegalArgumentException("Invalid QR Token"));
        return orderRepository.findByTableIdAndStatusIn(
                table.getId(), List.of(OrderStatus.SOFT_ORDER, OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.READY, OrderStatus.SERVED))
                .map(this::mapToOrderResponse)
                .orElse(null);
    }

    // --- 2. Process Cashier Dine-In Order ---
    public OrderResponse createPosDineInOrder(UUID tableId, List<OrderItemRequest> itemRequests) {
        RestaurantTable table = tableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found"));

        // Check if there is already an active order for this table
        Order order = orderRepository.findByTableIdAndStatusIn(
                table.getId(), List.of(OrderStatus.SOFT_ORDER, OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.READY, OrderStatus.SERVED)).orElse(null);

        // If no active order exists, create a new one and occupy the table
        if (order == null) {
            order = Order.builder()
                    .orderNumber("POS-" + System.currentTimeMillis() + "-" + java.util.UUID.randomUUID().toString().substring(0,4).toUpperCase())
                    .orderType(OrderType.DINE_IN)
                    .status(OrderStatus.CONFIRMED) // Cashier orders bypass SOFT_ORDER directly to CONFIRMED
                    .kitchenStatus(determineInitialKitchenStatus())
                    .table(table)
                    .subTotal(BigDecimal.ZERO)
                    .build();
            table.setStatus(TableStatus.OCCUPIED);
            tableRepository.save(table);
            order = orderRepository.save(order);
        }

        // Append the new items to the order
        appendItemsToOrder(order, itemRequests);
        return mapToOrderResponse(orderRepository.save(order));
    }

    public OrderResponse getOrderById(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        return mapToOrderResponse(order);
    }

    public List<OrderResponse> getOrders(List<OrderStatus> statuses) {
        // If no status is provided, default to active restaurant workflows
        if (statuses == null || statuses.isEmpty()) {
            statuses = List.of(OrderStatus.SOFT_ORDER, OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.READY, OrderStatus.SERVED, OrderStatus.BILLED, OrderStatus.PAID);
        }

        return orderRepository.findActiveOrdersByStatusIn(statuses).stream()
                .map(this::mapToOrderResponse)
                .toList();
    }

    public PagedResponse<OrderResponse> getPagedOrders(
            List<OrderStatus> statuses,
            OrderType orderType,
            String search,
            int page,
            int size,
            String sortBy,
            String sortDirection) {

        Sort.Direction direction = "ASC".equalsIgnoreCase(sortDirection) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String property = sanitizeSortProperty(sortBy);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by(direction, property));

        Specification<Order> spec = OrderSpecification.filter(statuses, orderType, search);
        Page<Order> orderPage = orderRepository.findAll(spec, pageable);

        Page<OrderResponse> responsePage = orderPage.map(this::mapToOrderResponse);
        return PagedResponse.of(responsePage);
    }

    private String sanitizeSortProperty(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) return "createdAt";
        return switch (sortBy.trim()) {
            case "created_at", "createdAt" -> "createdAt";
            case "order_number", "orderNumber" -> "orderNumber";
            case "grand_total", "grandTotal" -> "grandTotal";
            case "sub_total", "subTotal" -> "subTotal";
            case "status" -> "status";
            case "updated_at", "updatedAt" -> "updatedAt";
            default -> "createdAt";
        };
    }

    public OrderResponse mergeOrders(UUID targetOrderId, UUID sourceOrderId) {
        if (targetOrderId.equals(sourceOrderId)) {
            throw new IllegalArgumentException("Cannot merge an order with itself");
        }

        Order targetOrder = orderRepository.findById(targetOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Target order not found"));
        Order sourceOrder = orderRepository.findById(sourceOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Source order not found"));

        if (targetOrder.getStatus() == OrderStatus.BILLED || targetOrder.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Target order is finalized and cannot accept merged items.");
        }
        if (sourceOrder.getStatus() == OrderStatus.BILLED || sourceOrder.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Source order is finalized and cannot be merged.");
        }

        // Copy all items from sourceOrder into targetOrder
        for (OrderItem sourceItem : sourceOrder.getItems()) {
            OrderItem newItem = OrderItem.builder()
                    .item(sourceItem.getItem())
                    .quantity(sourceItem.getQuantity())
                    .unitPrice(sourceItem.getUnitPrice())
                    .totalPrice(sourceItem.getTotalPrice())
                    .notes(sourceItem.getNotes())
                    .build();
            targetOrder.addOrderItem(newItem);
        }

        // Update target subtotal
        BigDecimal newSubtotal = targetOrder.getItems().stream()
                .map(OrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        targetOrder.setSubTotal(newSubtotal);
        BigDecimal effectiveTaxRate = configService.getConfig().getTaxRate();
        targetOrder.setTaxAmount(targetOrder.getSubTotal().multiply(effectiveTaxRate).setScale(2, java.math.RoundingMode.HALF_UP));
        BigDecimal discount = targetOrder.getDiscountAmount() != null ? targetOrder.getDiscountAmount() : BigDecimal.ZERO;
        targetOrder.setGrandTotal(targetOrder.getSubTotal().add(targetOrder.getTaxAmount()).subtract(discount));

        // If target order was already marked served/ready, reset to CONFIRMED for newly merged items to be visible in kitchen
        if (targetOrder.getStatus() == OrderStatus.SERVED || targetOrder.getStatus() == OrderStatus.READY) {
            targetOrder.setStatus(OrderStatus.CONFIRMED);
            targetOrder.setKitchenStatus(OrderStatus.CONFIRMED.name());
        }

        // Cancel the source order so it is not double-billed
        sourceOrder.setStatus(OrderStatus.CANCELLED);
        sourceOrder.setKitchenStatus(OrderStatus.CANCELLED.name());
        if (sourceOrder.getTable() != null && (targetOrder.getTable() == null || !sourceOrder.getTable().getId().equals(targetOrder.getTable().getId()))) {
            RestaurantTable sourceTable = sourceOrder.getTable();
            sourceTable.setStatus(TableStatus.AVAILABLE);
            tableRepository.save(sourceTable);
        }

        orderRepository.save(sourceOrder);
        return mapToOrderResponse(orderRepository.save(targetOrder));
    }

    public Map<String, Object> getAppConfig() {
        return Map.of(
                "taxRate", configService.getConfig().getTaxRate(),
                "defaultDiscountPercentage", configService.getConfig().getDefaultDiscountPercentage(),
                "kitchenWorkflowEnabled", configService.getConfig().isKitchenWorkflowEnabled(),
                "taxName", configService.getConfig().getTaxName(),
                "restaurantName", "WildBar"
        );
    }
}
