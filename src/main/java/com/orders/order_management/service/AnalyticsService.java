package com.orders.order_management.service;

import com.orders.order_management.dto.SalesDashboardResponse;
import com.orders.order_management.repos.OrderItemRepository;
import com.orders.order_management.repos.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AnalyticsService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public SalesDashboardResponse getSalesDashboard() {
        Instant now = Instant.now();

        // Define time boundaries using UTC
        Instant startOfDay = ZonedDateTime.ofInstant(now, ZoneId.of("UTC")).truncatedTo(ChronoUnit.DAYS).toInstant();
        Instant startOfMonth = ZonedDateTime.ofInstant(now, ZoneId.of("UTC")).withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS).toInstant();
        Instant startOfYear = ZonedDateTime.ofInstant(now, ZoneId.of("UTC")).withDayOfYear(1).truncatedTo(ChronoUnit.DAYS).toInstant();

        BigDecimal dailySales = orderRepository.calculateTotalSales(startOfDay, now);
        BigDecimal monthlySales = orderRepository.calculateTotalSales(startOfMonth, now);
        BigDecimal yearlySales = orderRepository.calculateTotalSales(startOfYear, now);

        List<OrderItemRepository.FastMovingItemProjection> fastMovingItems =
                orderItemRepository.getFastMovingItemsLast30Days();

        return new SalesDashboardResponse(dailySales, monthlySales, yearlySales, fastMovingItems);
    }
}