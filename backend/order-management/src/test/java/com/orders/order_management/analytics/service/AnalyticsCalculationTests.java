package com.orders.order_management.analytics.service;

import com.orders.order_management.analytics.dto.SalesDashboardResponse;
import com.orders.order_management.core.enums.OrderType;
import com.orders.order_management.transaction.repository.OrderItemRepository;
import com.orders.order_management.transaction.repository.OrderRepository;
import com.orders.order_management.transaction.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AnalyticsCalculationTests {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        lenient().when(orderItemRepository.getFastMovingItemsBetween(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        lenient().when(orderRepository.findRecentOrdersBetween(any(), any(), any(), any(Pageable.class)))
                .thenReturn(Collections.emptyList());
    }

    @Test
    @DisplayName("Analytics Dashboard: Correctly Computes AOV and Avoids Division by Zero")
    void testAverageOrderValueAndMetrics() {
        Instant start = Instant.now().minusSeconds(86400 * 7);
        Instant end = Instant.now();

        // Total sales = 15,450.00, Billed orders = 10 -> AOV = 1,545.00
        when(orderRepository.calculateTotalSales(any(), any(), any()))
                .thenReturn(new BigDecimal("15450.00"));
        when(orderRepository.countOrdersBetween(any(), any(), any())).thenReturn(12L);
        when(orderRepository.countBilledOrdersBetween(any(), any(), any())).thenReturn(10L);
        when(orderRepository.countByOrderTypeBetween(eq(OrderType.DINE_IN), any(), any(), any())).thenReturn(8L);
        when(orderRepository.countByOrderTypeBetween(eq(OrderType.TAKE_AWAY), any(), any(), any())).thenReturn(4L);
        when(orderRepository.getAverageTurnaroundMinutesBetween(any(), any(), any())).thenReturn(24.5);

        SalesDashboardResponse response = analyticsService.getSalesDashboard(start, end, "outlet-1");

        assertNotNull(response);
        assertEquals(new BigDecimal("15450.00"), response.getPeriodSales());
        assertEquals(12L, response.getTotalOrdersCount());
        assertEquals(10L, response.getBilledOrdersCount());
        assertEquals(8L, response.getDineInOrdersCount());
        assertEquals(4L, response.getTakeAwayOrdersCount());
        // AOV = 15450 / 10 = 1545.00
        assertEquals(new BigDecimal("15450.00").divide(BigDecimal.valueOf(10), 2, RoundingMode.HALF_UP), response.getAverageOrderValue());
        assertEquals(24.5, response.getAverageTurnaroundMinutes());
    }

    @Test
    @DisplayName("Analytics Dashboard: Zero Billed Orders Safely Yields Zero AOV Without Exception")
    void testZeroBilledOrdersNoDivisionByZero() {
        Instant start = Instant.now().minusSeconds(86400);
        Instant end = Instant.now();

        when(orderRepository.calculateTotalSales(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(orderRepository.countOrdersBetween(any(), any(), any())).thenReturn(0L);
        when(orderRepository.countBilledOrdersBetween(any(), any(), any())).thenReturn(0L);
        when(orderRepository.countByOrderTypeBetween(any(), any(), any(), any())).thenReturn(0L);

        SalesDashboardResponse response = analyticsService.getSalesDashboard(start, end, null);

        assertNotNull(response);
        assertEquals(BigDecimal.ZERO, response.getPeriodSales());
        assertEquals(0L, response.getTotalOrdersCount());
        assertEquals(0L, response.getBilledOrdersCount());
        assertEquals(BigDecimal.ZERO, response.getAverageOrderValue());
    }
}
