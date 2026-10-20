package com.orders.order_management.transaction.service;

import com.orders.order_management.core.entity.Outlet;
import com.orders.order_management.core.entity.SystemConfig;
import com.orders.order_management.core.enums.OrderStatus;
import com.orders.order_management.core.enums.OrderType;
import com.orders.order_management.core.enums.PaymentMethod;
import com.orders.order_management.core.enums.PaymentStatus;
import com.orders.order_management.core.enums.TableStatus;
import com.orders.order_management.core.service.SystemConfigService;
import com.orders.order_management.core.repository.OutletRepository;
import com.orders.order_management.menu.entity.Item;
import com.orders.order_management.menu.repository.ItemRepository;
import com.orders.order_management.transaction.dto.OrderItemRequest;
import com.orders.order_management.transaction.dto.OrderResponse;
import com.orders.order_management.transaction.dto.PaymentResponse;
import com.orders.order_management.transaction.entity.Order;
import com.orders.order_management.transaction.entity.OrderItem;
import com.orders.order_management.transaction.entity.RestaurantTable;
import com.orders.order_management.transaction.repository.OrderItemRepository;
import com.orders.order_management.transaction.repository.OrderRepository;
import com.orders.order_management.transaction.repository.PaymentRepository;
import com.orders.order_management.transaction.repository.RestaurantTableRepository;
import com.orders.order_management.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TransactionCalculationTests {

    @Mock
    private RestaurantTableRepository tableRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ItemRepository itemRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private SystemConfigService configService;
    @Mock
    private OutletRepository outletRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private KitchenEventService kitchenEventService;

    @InjectMocks
    private TransactionService transactionService;

    private Outlet testOutlet;
    private RestaurantTable testTable;
    private Item burger;
    private Item soda;
    private SystemConfig standardConfig;

    @BeforeEach
    void setUp() {
        testOutlet = new Outlet();
        testOutlet.setId("outlet-test-1");
        testOutlet.setName("Main Outlet");
        testOutlet.setCode("MAIN");

        testTable = RestaurantTable.builder()
                .tableNumber("T1")
                .qrToken(UUID.randomUUID())
                .status(TableStatus.AVAILABLE)
                .outlet(testOutlet)
                .build();
        testTable.setId(UUID.randomUUID());

        burger = Item.builder()
                .name("Cheeseburger")
                .price(new BigDecimal("250.00"))
                .isAvailable(true)
                .build();
        burger.setId(UUID.randomUUID());

        soda = Item.builder()
                .name("Cold Soda")
                .price(new BigDecimal("50.00"))
                .isAvailable(true)
                .build();
        soda.setId(UUID.randomUUID());

        standardConfig = SystemConfig.builder()
                .id(1L)
                .taxRate(new BigDecimal("0.05")) // 5% GST
                .taxName("GST")
                .defaultDiscountPercentage(BigDecimal.ZERO)
                .kitchenWorkflowEnabled(true)
                .restaurantName("WildBar")
                .build();

        lenient().when(configService.getConfig()).thenReturn(standardConfig);
    }

    @Test
    @DisplayName("Tax Rate Normalization: Converts 5 -> 0.05, 18 -> 0.18, and handles null/negative cleanly")
    void testTaxRateNormalization() {
        // Normal 0.05
        standardConfig.setTaxRate(new BigDecimal("0.05"));
        assertEquals(new BigDecimal("0.05"), transactionService.getEffectiveTaxRate());

        // Whole percentage 5
        standardConfig.setTaxRate(new BigDecimal("5"));
        assertEquals(new BigDecimal("0.0500"), transactionService.getEffectiveTaxRate());

        // 18% GST
        standardConfig.setTaxRate(new BigDecimal("18"));
        assertEquals(new BigDecimal("0.1800"), transactionService.getEffectiveTaxRate());

        // Null tax rate
        standardConfig.setTaxRate(null);
        assertEquals(BigDecimal.ZERO, transactionService.getEffectiveTaxRate());

        // Negative tax rate
        standardConfig.setTaxRate(new BigDecimal("-0.05"));
        assertEquals(BigDecimal.ZERO, transactionService.getEffectiveTaxRate());
    }

    @Test
    @DisplayName("TakeAway Order Calculations: Correct Subtotal, 5% Tax, and Grand Total with Half-Up Rounding")
    void testCreateTakeAwayOrderCalculations() {
        when(outletRepository.findById("outlet-test-1")).thenReturn(Optional.of(testOutlet));
        when(itemRepository.findById(burger.getId())).thenReturn(Optional.of(burger));
        when(itemRepository.findById(soda.getId())).thenReturn(Optional.of(soda));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            if (o.getId() == null) o.setId(UUID.randomUUID());
            return o;
        });

        // Order: 2 Burgers (2 * 250 = 500) + 1 Soda (1 * 50 = 50) => Subtotal = 550.00
        List<OrderItemRequest> requests = List.of(
                OrderItemRequest.builder().itemId(burger.getId()).quantity(2).build(),
                OrderItemRequest.builder().itemId(soda.getId()).quantity(1).build()
        );

        OrderResponse response = transactionService.createTakeAwayOrder(requests, "outlet-test-1");

        assertNotNull(response);
        assertEquals(new BigDecimal("550.00"), response.getSubTotal());
        // 5% of 550.00 = 27.50
        assertEquals(new BigDecimal("27.50"), response.getTaxAmount());
        // Grand total = 550.00 + 27.50 = 577.50
        assertEquals(new BigDecimal("577.50"), response.getGrandTotal());
        assertEquals(BigDecimal.ZERO, response.getDiscountAmount());
        assertEquals("CONFIRMED", response.getStatus());
        assertEquals(2, response.getItems().size());
        assertEquals(1, response.getItems().get(0).getBatchNumber());
        assertEquals(1, response.getItems().get(1).getBatchNumber());
    }

    @Test
    @DisplayName("Incremental Order Appending: Correctly Increments Batch Numbers and Recalculates Totals")
    void testAppendItemsIncrementalBatchAndCalculations() {
        Order existingOrder = Order.builder()
                .orderNumber("ORD-MAIN-1001")
                .orderType(OrderType.DINE_IN)
                .status(OrderStatus.CONFIRMED)
                .kitchenStatus("CONFIRMED")
                .subTotal(new BigDecimal("250.00"))
                .taxAmount(new BigDecimal("12.50"))
                .discountAmount(BigDecimal.ZERO)
                .grandTotal(new BigDecimal("262.50"))
                .outlet(testOutlet)
                .build();
        existingOrder.setId(UUID.randomUUID());

        OrderItem firstItem = OrderItem.builder()
                .item(burger)
                .quantity(1)
                .unitPrice(burger.getPrice())
                .totalPrice(new BigDecimal("250.00"))
                .batchNumber(1)
                .build();
        existingOrder.addOrderItem(firstItem);

        when(orderRepository.findById(existingOrder.getId())).thenReturn(Optional.of(existingOrder));
        when(itemRepository.findById(soda.getId())).thenReturn(Optional.of(soda));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Add 2 Sodas in Batch 2 (2 * 50 = 100)
        List<OrderItemRequest> newRequests = List.of(
                OrderItemRequest.builder().itemId(soda.getId()).quantity(2).build()
        );

        OrderResponse response = transactionService.appendItemsToOrder(existingOrder.getId(), newRequests);

        // Subtotal = 250 + 100 = 350.00
        assertEquals(new BigDecimal("350.00"), response.getSubTotal());
        // Tax = 5% of 350.00 = 17.50
        assertEquals(new BigDecimal("17.50"), response.getTaxAmount());
        // Grand Total = 350 + 17.50 = 367.50
        assertEquals(new BigDecimal("367.50"), response.getGrandTotal());

        // Verify batch number
        assertEquals(2, response.getItems().size());
        assertEquals(1, response.getItems().get(0).getBatchNumber());
        assertEquals(2, response.getItems().get(1).getBatchNumber());
    }

    @Test
    @DisplayName("Final Bill Generation: Correctly Applies Discount and Clamps Grand Total Above Zero")
    void testGenerateFinalBillDiscountCalculations() {
        Order order = Order.builder()
                .orderNumber("ORD-MAIN-1002")
                .status(OrderStatus.CONFIRMED)
                .subTotal(new BigDecimal("1000.00"))
                .taxAmount(new BigDecimal("50.00"))
                .grandTotal(new BigDecimal("1050.00"))
                .outlet(testOutlet)
                .build();
        order.setId(UUID.randomUUID());

        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Case 1: Standard discount of 150.00
        OrderResponse bill1 = transactionService.generateFinalBill(order.getId(), new BigDecimal("150.00"));
        assertEquals(new BigDecimal("1000.00"), bill1.getSubTotal());
        assertEquals(new BigDecimal("50.00"), bill1.getTaxAmount());
        assertEquals(new BigDecimal("150.00"), bill1.getDiscountAmount());
        // 1000 + 50 - 150 = 900.00
        assertEquals(new BigDecimal("900.00"), bill1.getGrandTotal());
        assertEquals("BILLED", bill1.getStatus());

        // Case 2: Excessive discount of 2000.00 (Exceeds gross total 1050.00)
        // Must clamp discount to 1050.00 and grand total to 0.00 (NO NEGATIVE AMOUNTS)
        OrderResponse bill2 = transactionService.generateFinalBill(order.getId(), new BigDecimal("2000.00"));
        assertEquals(new BigDecimal("1050.00"), bill2.getDiscountAmount());
        assertEquals(new BigDecimal("0.00"), bill2.getGrandTotal());

        // Case 3: Negative discount attempt (-100.00) -> Clamped to 0.00
        OrderResponse bill3 = transactionService.generateFinalBill(order.getId(), new BigDecimal("-100.00"));
        assertEquals(BigDecimal.ZERO, bill3.getDiscountAmount());
        assertEquals(new BigDecimal("1050.00"), bill3.getGrandTotal());
    }

    @Test
    @DisplayName("Void Order Item: Correctly Recalculates Subtotal, Tax, and Clamps Discount")
    void testVoidOrderItemCalculations() {
        Order order = Order.builder()
                .orderNumber("ORD-MAIN-1003")
                .status(OrderStatus.CONFIRMED)
                .subTotal(new BigDecimal("300.00"))
                .taxAmount(new BigDecimal("15.00"))
                .discountAmount(new BigDecimal("50.00"))
                .grandTotal(new BigDecimal("265.00"))
                .outlet(testOutlet)
                .build();
        order.setId(UUID.randomUUID());

        UUID item1Id = UUID.randomUUID();
        OrderItem oi1 = OrderItem.builder()
                .item(burger)
                .quantity(1)
                .totalPrice(new BigDecimal("250.00"))
                .build();
        oi1.setId(item1Id);
        order.addOrderItem(oi1);

        UUID item2Id = UUID.randomUUID();
        OrderItem oi2 = OrderItem.builder()
                .item(soda)
                .quantity(1)
                .totalPrice(new BigDecimal("50.00"))
                .build();
        oi2.setId(item2Id);
        order.addOrderItem(oi2);

        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Void Burger (250.00), leaving only Soda (50.00)
        OrderResponse response = transactionService.voidOrderItem(order.getId(), item1Id);

        // New Subtotal = 50.00
        assertEquals(new BigDecimal("50.00"), response.getSubTotal());
        // New Tax = 5% of 50.00 = 2.50
        assertEquals(new BigDecimal("2.50"), response.getTaxAmount());
        // Gross = 52.50. Original discount was 50.00, which is <= 52.50, so discount stays 50.00
        assertEquals(new BigDecimal("50.00"), response.getDiscountAmount());
        // Grand total = 52.50 - 50.00 = 2.50
        assertEquals(new BigDecimal("2.50"), response.getGrandTotal());
        assertEquals(1, response.getItems().size());
    }

    @Test
    @DisplayName("Merge Orders: Correctly Merges Items, Batches, Recalculates Totals and Cancels Source Order")
    void testMergeOrdersCalculations() {
        Order targetOrder = Order.builder()
                .orderNumber("ORD-T1")
                .status(OrderStatus.CONFIRMED)
                .subTotal(new BigDecimal("250.00"))
                .taxAmount(new BigDecimal("12.50"))
                .grandTotal(new BigDecimal("262.50"))
                .outlet(testOutlet)
                .table(testTable)
                .build();
        targetOrder.setId(UUID.randomUUID());
        OrderItem targetItem = OrderItem.builder()
                .item(burger)
                .quantity(1)
                .totalPrice(new BigDecimal("250.00"))
                .batchNumber(1)
                .build();
        targetOrder.addOrderItem(targetItem);

        RestaurantTable sourceTable = RestaurantTable.builder()
                .tableNumber("T2")
                .status(TableStatus.OCCUPIED)
                .outlet(testOutlet)
                .build();
        sourceTable.setId(UUID.randomUUID());

        Order sourceOrder = Order.builder()
                .orderNumber("ORD-T2")
                .status(OrderStatus.CONFIRMED)
                .subTotal(new BigDecimal("50.00"))
                .taxAmount(new BigDecimal("2.50"))
                .grandTotal(new BigDecimal("52.50"))
                .outlet(testOutlet)
                .table(sourceTable)
                .build();
        sourceOrder.setId(UUID.randomUUID());
        OrderItem sourceItem = OrderItem.builder()
                .item(soda)
                .quantity(1)
                .totalPrice(new BigDecimal("50.00"))
                .batchNumber(1)
                .build();
        sourceOrder.addOrderItem(sourceItem);

        when(orderRepository.findById(targetOrder.getId())).thenReturn(Optional.of(targetOrder));
        when(orderRepository.findById(sourceOrder.getId())).thenReturn(Optional.of(sourceOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse mergedResponse = transactionService.mergeOrders(targetOrder.getId(), sourceOrder.getId());

        // Target order calculations: 250 + 50 = 300.00 subtotal
        assertEquals(new BigDecimal("300.00"), mergedResponse.getSubTotal());
        // Tax = 5% of 300.00 = 15.00
        assertEquals(new BigDecimal("15.00"), mergedResponse.getTaxAmount());
        // Grand Total = 315.00
        assertEquals(new BigDecimal("315.00"), mergedResponse.getGrandTotal());
        // Items count = 2
        assertEquals(2, mergedResponse.getItems().size());
        // Source item merged as Batch 2
        assertEquals(2, mergedResponse.getItems().get(1).getBatchNumber());

        // Source order must be CANCELLED and its table freed
        assertEquals(OrderStatus.CANCELLED, sourceOrder.getStatus());
        assertEquals(TableStatus.AVAILABLE, sourceTable.getStatus());
    }

    @Test
    @DisplayName("Cross-Outlet Order Merge: Strictly Rejected to Prevent Data Corruption")
    void testMergeOrdersCrossOutletRejection() {
        Outlet otherOutlet = new Outlet();
        otherOutlet.setId("outlet-test-2");

        Order targetOrder = Order.builder().status(OrderStatus.CONFIRMED).outlet(testOutlet).build();
        targetOrder.setId(UUID.randomUUID());

        Order sourceOrder = Order.builder().status(OrderStatus.CONFIRMED).outlet(otherOutlet).build();
        sourceOrder.setId(UUID.randomUUID());

        when(orderRepository.findById(targetOrder.getId())).thenReturn(Optional.of(targetOrder));
        when(orderRepository.findById(sourceOrder.getId())).thenReturn(Optional.of(sourceOrder));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                transactionService.mergeOrders(targetOrder.getId(), sourceOrder.getId()));
        assertTrue(ex.getMessage().contains("Cannot merge orders from different outlets"));
    }

    @Test
    @DisplayName("Payment Settlement: Validates Amounts, Updates Status to PAID, and Frees Table")
    void testSettlePaymentWorkflowAndValidation() {
        testTable.setStatus(TableStatus.OCCUPIED);
        Order order = Order.builder()
                .orderNumber("ORD-MAIN-1005")
                .status(OrderStatus.BILLED)
                .grandTotal(new BigDecimal("500.00"))
                .table(testTable)
                .outlet(testOutlet)
                .build();
        order.setId(UUID.randomUUID());

        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Negative payment rejected
        assertThrows(IllegalArgumentException.class, () ->
                transactionService.settlePayment(order.getId(), PaymentMethod.CASH, new BigDecimal("-50.00")));

        // Successful full payment
        PaymentResponse payResponse = transactionService.settlePayment(order.getId(), PaymentMethod.UPI, new BigDecimal("500.00"));

        assertNotNull(payResponse);
        assertEquals(new BigDecimal("500.00"), payResponse.getAmountPaid());
        assertEquals(PaymentMethod.UPI, payResponse.getPaymentMethod());
        assertEquals(PaymentStatus.SUCCESS, payResponse.getPaymentStatus());

        // Order marked PAID and Table marked AVAILABLE
        assertEquals(OrderStatus.PAID, order.getStatus());
        assertEquals(TableStatus.AVAILABLE, testTable.getStatus());

        // Duplicate settlement on already PAID order rejected
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                transactionService.settlePayment(order.getId(), PaymentMethod.CASH, new BigDecimal("500.00")));
        assertTrue(ex.getMessage().contains("already settled"));
    }

    @Test
    @DisplayName("Validation: Empty or Negative Quantity Item Requests Rejected")
    void testItemRequestValidation() {
        UUID tableId = testTable.getId();

        // Empty list rejected
        assertThrows(IllegalArgumentException.class, () ->
                transactionService.createPosDineInOrder(tableId, Collections.emptyList()));

        // Zero or negative quantity rejected
        List<OrderItemRequest> invalidRequests = List.of(
                OrderItemRequest.builder().itemId(burger.getId()).quantity(0).build()
        );
        assertThrows(IllegalArgumentException.class, () ->
                transactionService.createPosDineInOrder(tableId, invalidRequests));
    }
}
