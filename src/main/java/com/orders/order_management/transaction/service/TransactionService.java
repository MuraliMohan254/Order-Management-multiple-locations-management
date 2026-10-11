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
    private final com.orders.order_management.core.repository.OutletRepository outletRepository;
    private final com.orders.order_management.auth.repository.UserRepository userRepository;
    private final KitchenEventService kitchenEventService;

    public com.orders.order_management.core.entity.Outlet resolveTargetOutlet(String requestedOutletId) {
        if (requestedOutletId != null && !requestedOutletId.isBlank() 
                && !"all".equalsIgnoreCase(requestedOutletId) && !"consolidated".equalsIgnoreCase(requestedOutletId)
                && !"null".equalsIgnoreCase(requestedOutletId) && !"undefined".equalsIgnoreCase(requestedOutletId)) {
            return outletRepository.findById(requestedOutletId.trim())
                    .orElseThrow(() -> new IllegalArgumentException("Outlet not found with id: " + requestedOutletId));
        }
        String currentOutletId = com.orders.order_management.core.security.SecurityUtils.getCurrentOutletId();
        if (currentOutletId != null) {
            return outletRepository.findById(currentOutletId)
                    .orElseThrow(() -> new IllegalArgumentException("Outlet not found with id: " + currentOutletId));
        }
        // Fallback for Admin when no outlet specified: pick first active outlet
        return outletRepository.findByIsActiveTrue().stream().findFirst()
                .orElseGet(() -> outletRepository.findAll().stream().findFirst()
                        .orElseThrow(() -> new IllegalStateException("No outlets exist in system. Please configure an outlet first.")));
    }

    private String determineInitialKitchenStatus() {
        if (!configService.getConfig().isKitchenWorkflowEnabled()) {
            return "SERVED"; // Bypass kitchen KDS
        }
        return com.orders.order_management.core.enums.OrderStatus.CONFIRMED.name();
    }

    private String getOutletCodePrefix(com.orders.order_management.core.entity.Outlet outlet) {
        if (outlet != null && outlet.getCode() != null && !outlet.getCode().isBlank()) {
            String clean = outlet.getCode().trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
            if (!clean.isEmpty()) {
                return clean.length() > 3 ? clean.substring(0, 3) : clean;
            }
        }
        if (outlet != null && outlet.getName() != null && !outlet.getName().isBlank()) {
            String clean = outlet.getName().trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
            if (!clean.isEmpty()) {
                return clean.length() > 3 ? clean.substring(0, 3) : clean;
            }
        }
        return "IND";
    }

    public TableResponse createTable(String tableNumber, String requestedOutletId) {
        com.orders.order_management.core.entity.Outlet targetOutlet = resolveTargetOutlet(requestedOutletId);
        RestaurantTable table = RestaurantTable.builder()
                .tableNumber(tableNumber)
                .qrToken(UUID.randomUUID())
                .status(TableStatus.AVAILABLE)
                .outlet(targetOutlet)
                .build();
        return mapToTableResponse(tableRepository.save(table));
    }

    public TableResponse createTable(String tableNumber) {
        return createTable(tableNumber, null);
    }

    public OrderResponse processQrOrder(UUID qrToken, List<OrderItemRequest> itemRequests) {
        RestaurantTable table = tableRepository.findByQrToken(qrToken)
                .orElseThrow(() -> new IllegalArgumentException("Invalid QR Token"));

        Order order = orderRepository.findByTableIdAndStatusIn(
                table.getId(), List.of(OrderStatus.SOFT_ORDER, OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.READY, OrderStatus.SERVED)).orElse(null);

        if (order == null) {
            order = Order.builder()
                    .orderNumber("ORD-" + getOutletCodePrefix(table.getOutlet()) + "-" + System.currentTimeMillis() + "-" + java.util.UUID.randomUUID().toString().substring(0,4).toUpperCase())
                    .orderType(OrderType.DINE_IN)
                    .status(OrderStatus.SOFT_ORDER)
                    .kitchenStatus(determineInitialKitchenStatus())
                    .table(table)
                    .outlet(table.getOutlet())
                    .subTotal(BigDecimal.ZERO)
                    .build();
            table.setStatus(TableStatus.OCCUPIED);
            tableRepository.save(table);
            order = orderRepository.save(order);
        }

        appendItemsToOrder(order, itemRequests);
        OrderResponse response = mapToOrderResponse(orderRepository.save(order));
        kitchenEventService.publishOrderUpdate(response);
        return response;
    }

    public OrderResponse appendItemsToOrder(UUID orderId, List<OrderItemRequest> requests) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        if (order.getStatus() == OrderStatus.BILLED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Cannot modify a finalized order.");
        }

        appendItemsToOrder(order, requests);
        OrderResponse response = mapToOrderResponse(orderRepository.save(order));
        kitchenEventService.publishOrderUpdate(response);
        return response;
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

        BigDecimal defaultPct = configService.getConfig().getDefaultDiscountPercentage();
        BigDecimal effectivePct = BigDecimal.ZERO;
        if (defaultPct != null && defaultPct.compareTo(BigDecimal.ZERO) > 0) {
            effectivePct = defaultPct.compareTo(BigDecimal.ONE) > 0
                    ? defaultPct.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)
                    : defaultPct;
        }

        BigDecimal discount = order.getDiscountAmount();
        // If discount not set yet, or if it's a soft order / QR order with 0 or null discount, calculate from default percentage
        if (discount == null || (order.getStatus() == OrderStatus.SOFT_ORDER && discount.compareTo(BigDecimal.ZERO) == 0)) {
            if (effectivePct.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal gross = order.getSubTotal().add(order.getTaxAmount());
                discount = gross.multiply(effectivePct).setScale(2, RoundingMode.HALF_UP);
                order.setDiscountAmount(discount);
            } else {
                discount = BigDecimal.ZERO;
                order.setDiscountAmount(BigDecimal.ZERO);
            }
        } else if (order.getStatus() == OrderStatus.SOFT_ORDER && effectivePct.compareTo(BigDecimal.ZERO) > 0) {
            // When additional items are appended to a customer QR order, recalculate default promo discount on new gross
            BigDecimal gross = order.getSubTotal().add(order.getTaxAmount());
            discount = gross.multiply(effectivePct).setScale(2, RoundingMode.HALF_UP);
            order.setDiscountAmount(discount);
        }
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
        BigDecimal finalDiscount = discount != null ? discount : (order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO);

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

        OrderResponse response = mapToOrderResponse(orderRepository.save(order));
        kitchenEventService.publishOrderUpdate(response);
        return response;
    }

    public PaymentResponse settlePayment(UUID orderId, PaymentMethod method, BigDecimal amount) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        String currentUser = com.orders.order_management.core.security.SecurityUtils.getCurrentUsername();
        com.orders.order_management.auth.entity.User userEntity = null;
        if (currentUser != null && !"Staff".equalsIgnoreCase(currentUser)) {
            userEntity = userRepository.findByUsername(currentUser).orElse(null);
        }
        Long settledById = userEntity != null ? userEntity.getId() : null;

        Payment payment = Payment.builder()
                .order(order)
                .outlet(order.getOutlet())
                .amountPaid(amount)
                .paymentMethod(method)
                .paymentStatus(PaymentStatus.SUCCESS)
                .settledBy(currentUser)
                .settledByUser(userEntity)
                .build();

        paymentRepository.save(payment);
        if (order.getPayments() != null) {
            order.getPayments().add(payment);
        }

        if (amount.compareTo(order.getGrandTotal()) >= 0 && order.getTable() != null) {
            RestaurantTable table = order.getTable();
            table.setStatus(TableStatus.AVAILABLE);
            tableRepository.save(table);
        }
        
        // Update order status to PAID once settled
        order.setStatus(com.orders.order_management.core.enums.OrderStatus.PAID);
        Order savedOrder = orderRepository.save(order);
        kitchenEventService.publishOrderUpdate(mapToOrderResponse(savedOrder));

        return PaymentResponse.builder()
                .id(payment.getId())
                .orderId(orderId)
                .amountPaid(amount)
                .paymentMethod(method)
                .paymentStatus(PaymentStatus.SUCCESS)
                .settledById(settledById)
                .settledBy(currentUser)
                .build();
    }

    private TableResponse mapToTableResponse(RestaurantTable table) {
        return TableResponse.builder()
                .id(table.getId())
                .tableNumber(table.getTableNumber())
                .qrToken(table.getQrToken())
                .status(table.getStatus().name())
                .outletId(table.getOutlet() != null ? table.getOutlet().getId() : null)
                .outletName(table.getOutlet() != null ? table.getOutlet().getName() : null)
                .build();
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

        String settledBy = null;
        Long settledById = null;
        if (order.getPayments() != null && !order.getPayments().isEmpty()) {
            Payment latestSuccessful = order.getPayments().stream()
                    .filter(p -> p.getPaymentStatus() == PaymentStatus.SUCCESS && p.getSettledBy() != null && !p.getSettledBy().isBlank())
                    .reduce((first, second) -> second)
                    .orElse(null);
            if (latestSuccessful != null) {
                settledBy = latestSuccessful.getSettledBy();
                if (latestSuccessful.getSettledByUser() != null) {
                    settledById = latestSuccessful.getSettledByUser().getId();
                }
            }
        }

        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .orderType(order.getOrderType() != null ? order.getOrderType().name() : null)
                .status(order.getStatus() != null ? order.getStatus().name() : null)
                .kitchenStatus(order.getKitchenStatus() != null ? order.getKitchenStatus() : (order.getStatus() != null ? order.getStatus().name() : "PENDING"))
                .tableId(order.getTable() != null ? order.getTable().getId() : null)
                .tableNumber(order.getTable() != null ? order.getTable().getTableNumber() : null)
                .outletId(order.getOutlet() != null ? order.getOutlet().getId() : null)
                .outletName(order.getOutlet() != null ? order.getOutlet().getName() : null)
                .settledById(settledById)
                .settledBy(settledBy)
                .subTotal(order.getSubTotal())
                .taxAmount(order.getTaxAmount())
                .discountAmount(order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO)
                .grandTotal(order.getGrandTotal())
                .createdAt(order.getCreatedAt())
                .items(itemResponses)
                .build();
    }

    public OrderResponse createTakeAwayOrder(List<OrderItemRequest> itemRequests, String requestedOutletId) {
        com.orders.order_management.core.entity.Outlet targetOutlet = resolveTargetOutlet(requestedOutletId);

        Order order = Order.builder()
                .orderNumber("TA-" + getOutletCodePrefix(targetOutlet) + "-" + System.currentTimeMillis() + "-" + java.util.UUID.randomUUID().toString().substring(0,4).toUpperCase())
                .orderType(OrderType.TAKE_AWAY)
                .status(OrderStatus.CONFIRMED) // Take-aways usually go straight to kitchen
                .kitchenStatus(determineInitialKitchenStatus())
                .table(null) // No table required
                .outlet(targetOutlet)
                .subTotal(BigDecimal.ZERO)
                .build();

        order = orderRepository.save(order);
        appendItemsToOrder(order, itemRequests);

        OrderResponse response = mapToOrderResponse(orderRepository.save(order));
        kitchenEventService.publishOrderUpdate(response);
        return response;
    }

    public OrderResponse createTakeAwayOrder(List<OrderItemRequest> itemRequests) {
        return createTakeAwayOrder(itemRequests, null);
    }

    public OrderResponse updateOrderStatus(UUID orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Cannot change status of a cancelled order.");
        }

        // If the order is already BILLED or PAID, kitchen operations (PREPARING, READY, SERVED) update only kitchenStatus
        if (order.getStatus() == OrderStatus.BILLED || order.getStatus() == OrderStatus.PAID) {
            order.setKitchenStatus(newStatus.name());
            OrderResponse response = mapToOrderResponse(orderRepository.save(order));
            kitchenEventService.publishOrderUpdate(response);
            return response;
        }

        order.setStatus(newStatus);
        order.setKitchenStatus(newStatus.name());
        OrderResponse response = mapToOrderResponse(orderRepository.save(order));
        kitchenEventService.publishOrderUpdate(response);
        return response;
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

        OrderResponse response = mapToOrderResponse(orderRepository.save(order));
        kitchenEventService.publishOrderUpdate(response);
        return response;
    }

    // --- 3. VOID/REMOVE SPECIFIC ITEM (Return before billing) ---
    public OrderResponse voidOrderItem(UUID orderId, UUID orderItemId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        if (order.getStatus() == OrderStatus.BILLED 
                || order.getStatus() == OrderStatus.PAID 
                || order.getStatus() == OrderStatus.SERVED 
                || "SERVED".equalsIgnoreCase(order.getKitchenStatus())
                || order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Cannot void items from an order that is served, billed, paid, or cancelled.");
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

        OrderResponse response = mapToOrderResponse(orderRepository.save(order));
        kitchenEventService.publishOrderUpdate(response);
        return response;
    }

    public void clearKitchenQueue() {
        List<Order> activeOrders = orderRepository.findActiveOrdersByStatusIn(List.of(
                com.orders.order_management.core.enums.OrderStatus.CONFIRMED,
                com.orders.order_management.core.enums.OrderStatus.PREPARING,
                com.orders.order_management.core.enums.OrderStatus.READY,
                com.orders.order_management.core.enums.OrderStatus.BILLED,
                com.orders.order_management.core.enums.OrderStatus.PAID
        ));
        for (Order order : activeOrders) {
            order.setKitchenStatus("SERVED");
            if (order.getStatus() != com.orders.order_management.core.enums.OrderStatus.BILLED && 
                order.getStatus() != com.orders.order_management.core.enums.OrderStatus.PAID) {
                order.setStatus(com.orders.order_management.core.enums.OrderStatus.SERVED);
            }
        }
        orderRepository.saveAll(activeOrders);
        kitchenEventService.publishQueueCleared(com.orders.order_management.core.security.SecurityUtils.getCurrentOutletId());
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
    // --- 1. Fetch all tables for the POS Grid ---
    public List<TableResponse> getAllTables(String requestedOutletId) {
        if (com.orders.order_management.core.security.SecurityUtils.isCurrentUserAdmin()) {
            if (requestedOutletId != null && !requestedOutletId.isBlank()
                    && !"all".equalsIgnoreCase(requestedOutletId) && !"consolidated".equalsIgnoreCase(requestedOutletId)
                    && !"null".equalsIgnoreCase(requestedOutletId) && !"undefined".equalsIgnoreCase(requestedOutletId)) {
                return tableRepository.findByOutletId(requestedOutletId.trim()).stream()
                        .map(this::mapToTableResponse)
                        .toList();
            }
        }
        return tableRepository.findAll().stream()
                .map(this::mapToTableResponse)
                .toList();
    }

    public List<TableResponse> getAllTables() {
        return getAllTables(null);
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

        if (!com.orders.order_management.core.security.SecurityUtils.isCurrentUserAdmin()) {
            String staffOutletId = com.orders.order_management.core.security.SecurityUtils.getCurrentOutletId();
            if (table.getOutlet() != null && !table.getOutlet().getId().equals(staffOutletId)) {
                throw new org.springframework.security.access.AccessDeniedException("Unauthorized table access for your assigned outlet");
            }
        }

        // Check if there is already an active order for this table
        Order order = orderRepository.findByTableIdAndStatusIn(
                table.getId(), List.of(OrderStatus.SOFT_ORDER, OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.READY, OrderStatus.SERVED)).orElse(null);

        // If no active order exists, create a new one and occupy the table
        if (order == null) {
            order = Order.builder()
                    .orderNumber("POS-" + getOutletCodePrefix(table.getOutlet()) + "-" + System.currentTimeMillis() + "-" + java.util.UUID.randomUUID().toString().substring(0,4).toUpperCase())
                    .orderType(OrderType.DINE_IN)
                    .status(OrderStatus.CONFIRMED) // Cashier orders bypass SOFT_ORDER directly to CONFIRMED
                    .kitchenStatus(determineInitialKitchenStatus())
                    .table(table)
                    .outlet(table.getOutlet())
                    .subTotal(BigDecimal.ZERO)
                    .build();
            table.setStatus(TableStatus.OCCUPIED);
            tableRepository.save(table);
            order = orderRepository.save(order);
        }

        // Append the new items to the order
        appendItemsToOrder(order, itemRequests);
        OrderResponse response = mapToOrderResponse(orderRepository.save(order));
        kitchenEventService.publishOrderUpdate(response);
        return response;
    }

    public OrderResponse getOrderById(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        return mapToOrderResponse(order);
    }

    public List<OrderResponse> getOrders(List<OrderStatus> statuses, String requestedOutletId) {
        // If no status is provided, default to active restaurant workflows
        if (statuses == null || statuses.isEmpty()) {
            statuses = List.of(OrderStatus.SOFT_ORDER, OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.READY, OrderStatus.SERVED, OrderStatus.BILLED, OrderStatus.PAID);
        }

        List<Order> orders = orderRepository.findActiveOrdersByStatusIn(statuses);
        if (com.orders.order_management.core.security.SecurityUtils.isCurrentUserAdmin()
                && requestedOutletId != null && !requestedOutletId.isBlank()
                && !"all".equalsIgnoreCase(requestedOutletId) && !"consolidated".equalsIgnoreCase(requestedOutletId)) {
            orders = orders.stream()
                    .filter(o -> o.getOutlet() != null && requestedOutletId.trim().equals(o.getOutlet().getId()))
                    .toList();
        }

        return orders.stream()
                .map(this::mapToOrderResponse)
                .toList();
    }

    public List<OrderResponse> getOrders(List<OrderStatus> statuses) {
        return getOrders(statuses, null);
    }

    public PagedResponse<OrderResponse> getPagedOrders(
            List<OrderStatus> statuses,
            OrderType orderType,
            String search,
            String requestedOutletId,
            int page,
            int size,
            String sortBy,
            String sortDirection) {

        String effectiveOutletId = null;
        if (com.orders.order_management.core.security.SecurityUtils.isCurrentUserAdmin()) {
            if (requestedOutletId != null && !requestedOutletId.isBlank()
                    && !"all".equalsIgnoreCase(requestedOutletId) && !"consolidated".equalsIgnoreCase(requestedOutletId)
                    && !"null".equalsIgnoreCase(requestedOutletId) && !"undefined".equalsIgnoreCase(requestedOutletId)) {
                effectiveOutletId = requestedOutletId.trim();
            }
        } else {
            effectiveOutletId = com.orders.order_management.core.security.SecurityUtils.getCurrentOutletId();
        }

        Sort.Direction direction = "ASC".equalsIgnoreCase(sortDirection) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String property = sanitizeSortProperty(sortBy);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by(direction, property));

        Specification<Order> spec = OrderSpecification.filter(statuses, orderType, search, effectiveOutletId);
        Page<Order> orderPage = orderRepository.findAll(spec, pageable);

        Page<OrderResponse> responsePage = orderPage.map(this::mapToOrderResponse);
        return PagedResponse.of(responsePage);
    }

    public PagedResponse<OrderResponse> getPagedOrders(
            List<OrderStatus> statuses,
            OrderType orderType,
            String search,
            int page,
            int size,
            String sortBy,
            String sortDirection) {
        return getPagedOrders(statuses, orderType, search, null, page, size, sortBy, sortDirection);
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
        BigDecimal defaultPct = configService.getConfig().getDefaultDiscountPercentage();
        BigDecimal effectivePct = BigDecimal.ZERO;
        if (defaultPct != null && defaultPct.compareTo(BigDecimal.ZERO) > 0) {
            effectivePct = defaultPct.compareTo(BigDecimal.ONE) > 0
                    ? defaultPct.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)
                    : defaultPct;
        }

        BigDecimal discount = targetOrder.getDiscountAmount();
        if (discount == null || (targetOrder.getStatus() == OrderStatus.SOFT_ORDER && discount.compareTo(BigDecimal.ZERO) == 0)) {
            if (effectivePct.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal gross = targetOrder.getSubTotal().add(targetOrder.getTaxAmount());
                discount = gross.multiply(effectivePct).setScale(2, RoundingMode.HALF_UP);
                targetOrder.setDiscountAmount(discount);
            } else {
                discount = BigDecimal.ZERO;
                targetOrder.setDiscountAmount(BigDecimal.ZERO);
            }
        }
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
        kitchenEventService.publishOrderUpdate(mapToOrderResponse(sourceOrder));

        OrderResponse targetResponse = mapToOrderResponse(orderRepository.save(targetOrder));
        kitchenEventService.publishOrderUpdate(targetResponse);
        return targetResponse;
    }

    public Map<String, Object> getAppConfig() {
        var cfg = configService.getConfig();
        return java.util.Map.of(
                "taxRate", cfg.getTaxRate() != null ? cfg.getTaxRate() : new java.math.BigDecimal("0.05"),
                "defaultDiscountPercentage", cfg.getDefaultDiscountPercentage() != null ? cfg.getDefaultDiscountPercentage() : java.math.BigDecimal.ZERO,
                "kitchenWorkflowEnabled", cfg.isKitchenWorkflowEnabled(),
                "taxName", cfg.getTaxName() != null ? cfg.getTaxName() : "GST",
                "restaurantName", cfg.getRestaurantName() != null ? cfg.getRestaurantName() : "WildBar",
                "thermalPrintingEnabled", cfg.isThermalPrintingEnabled(),
                "thermalPaperSize", cfg.getThermalPaperSize() != null ? cfg.getThermalPaperSize() : "80mm",
                "printerName", cfg.getPrinterName() != null ? cfg.getPrinterName() : "",
                "gstNumber", cfg.getGstNumber() != null ? cfg.getGstNumber() : ""
        );
    }
}
