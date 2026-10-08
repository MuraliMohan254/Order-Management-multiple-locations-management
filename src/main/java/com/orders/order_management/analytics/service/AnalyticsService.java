package com.orders.order_management.analytics.service;


import com.orders.order_management.analytics.dto.BusinessInsightsResponse;
import com.orders.order_management.analytics.dto.SalesDashboardResponse;
import com.orders.order_management.analytics.dto.TopItemDto;
import com.orders.order_management.core.enums.OrderType;
import com.orders.order_management.menu.entity.Category;
import com.orders.order_management.transaction.dto.OrderResponse;
import com.orders.order_management.transaction.entity.Order;
import com.orders.order_management.transaction.entity.Payment;
import com.orders.order_management.transaction.repository.OrderItemRepository;
import com.orders.order_management.transaction.repository.OrderRepository;
import com.orders.order_management.transaction.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AnalyticsService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;

    public SalesDashboardResponse getSalesDashboard(Instant customStart, Instant customEnd) {
        Instant now = Instant.now();

        // Default to last 30 days if not provided
        Instant startDate = customStart != null ? customStart : ZonedDateTime.ofInstant(now, ZoneId.of("UTC")).minusDays(30).toInstant();
        Instant endDate = customEnd != null ? customEnd : now;

        // Daily, Monthly, Yearly Baselines
        Instant startOfDay = ZonedDateTime.ofInstant(now, ZoneId.of("UTC")).truncatedTo(ChronoUnit.DAYS).toInstant();
        Instant startOfMonth = ZonedDateTime.ofInstant(now, ZoneId.of("UTC")).withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS).toInstant();
        Instant startOfYear = ZonedDateTime.ofInstant(now, ZoneId.of("UTC")).withDayOfYear(1).truncatedTo(ChronoUnit.DAYS).toInstant();

        BigDecimal dailySales = orderRepository.calculateTotalSales(startOfDay, now);
        BigDecimal monthlySales = orderRepository.calculateTotalSales(startOfMonth, now);
        BigDecimal yearlySales = orderRepository.calculateTotalSales(startOfYear, now);

        // Period specific stats
        BigDecimal periodSales = orderRepository.calculateTotalSales(startDate, endDate);
        long totalOrders = orderRepository.countOrdersBetween(startDate, endDate);
        long billedOrders = orderRepository.countBilledOrdersBetween(startDate, endDate);
        long dineInOrders = orderRepository.countByOrderTypeBetween(OrderType.DINE_IN, startDate, endDate);
        long takeAwayOrders = orderRepository.countByOrderTypeBetween(OrderType.TAKE_AWAY, startDate, endDate);

        BigDecimal averageOrderValue = billedOrders > 0 
                ? periodSales.divide(BigDecimal.valueOf(billedOrders), 2, RoundingMode.HALF_UP) 
                : BigDecimal.ZERO;

        Double avgTurnaround = orderRepository.getAverageTurnaroundMinutesBetween(startDate, endDate);
        if (avgTurnaround == null || avgTurnaround <= 0.0) {
            avgTurnaround = orderRepository.getAverageTurnaroundTimeMinutesLast30Days();
        }

        // Top moving items
        List<TopItemDto> fastMovingItems = orderItemRepository.getFastMovingItemsBetween(startDate, endDate).stream()
                .map(p -> TopItemDto.builder()
                        .itemName(p.getItemName())
                        .totalSold(p.getTotalSold())
                        .totalRevenue(p.getTotalRevenue())
                        .build())
                .toList();

        // Recent orders
        List<OrderResponse> recentOrders = orderRepository.findOrdersBetween(startDate, endDate).stream()
                .limit(20)
                .map(this::mapOrderSummary)
                .toList();

        return SalesDashboardResponse.builder()
                .dailySales(dailySales != null ? dailySales : BigDecimal.ZERO)
                .monthlySales(monthlySales != null ? monthlySales : BigDecimal.ZERO)
                .yearlySales(yearlySales != null ? yearlySales : BigDecimal.ZERO)
                .periodSales(periodSales != null ? periodSales : BigDecimal.ZERO)
                .totalOrdersCount(totalOrders)
                .billedOrdersCount(billedOrders)
                .dineInOrdersCount(dineInOrders)
                .takeAwayOrdersCount(takeAwayOrders)
                .averageOrderValue(averageOrderValue)
                .averageTurnaroundMinutes(avgTurnaround)
                .fastMovingItems(fastMovingItems)
                .recentOrders(recentOrders)
                .build();
    }

    private OrderResponse mapOrderSummary(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .orderType(order.getOrderType() != null ? order.getOrderType().name() : null)
                .status(order.getStatus() != null ? order.getStatus().name() : null)
                .tableId(order.getTable() != null ? order.getTable().getId() : null)
                .tableNumber(order.getTable() != null ? order.getTable().getTableNumber() : null)
                .subTotal(order.getSubTotal())
                .taxAmount(order.getTaxAmount())
                .discountAmount(order.getDiscountAmount())
                .grandTotal(order.getGrandTotal())
                .createdAt(order.getCreatedAt())
                .items(Collections.emptyList())
                .build();
    }

    public BusinessInsightsResponse getBusinessInsights(Instant customStart, Instant customEnd) {
        Instant now = Instant.now();
        Instant startDate = customStart != null ? customStart : ZonedDateTime.ofInstant(now, ZoneId.of("UTC")).minusDays(30).toInstant();
        Instant endDate = customEnd != null ? customEnd : now;

        BigDecimal netSales = orderRepository.calculateTotalSales(startDate, endDate);
        BigDecimal totalDiscounts = orderRepository.calculateTotalDiscounts(startDate, endDate);
        BigDecimal grossSales = netSales.add(totalDiscounts);

        long totalOrders = orderRepository.countOrdersBetween(startDate, endDate);
        long billedOrders = orderRepository.countBilledOrdersBetween(startDate, endDate);
        long cancelledOrders = orderRepository.countCancelledOrdersBetween(startDate, endDate);

        BigDecimal overallAov = billedOrders > 0
                ? netSales.divide(BigDecimal.valueOf(billedOrders), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Double discountRate = (grossSales.compareTo(BigDecimal.ZERO) > 0)
                ? Math.round(totalDiscounts.multiply(BigDecimal.valueOf(100)).divide(grossSales, 2, RoundingMode.HALF_UP).doubleValue() * 10.0) / 10.0
                : 0.0;

        // Channel breakdown
        long dineInCount = orderRepository.countByOrderTypeBetween(OrderType.DINE_IN, startDate, endDate);
        long takeAwayCount = orderRepository.countByOrderTypeBetween(OrderType.TAKE_AWAY, startDate, endDate);
        BigDecimal dineInRevenue = orderRepository.calculateSalesByOrderType(OrderType.DINE_IN, startDate, endDate);
        BigDecimal takeAwayRevenue = orderRepository.calculateSalesByOrderType(OrderType.TAKE_AWAY, startDate, endDate);

        BigDecimal dineInAov = dineInCount > 0
                ? dineInRevenue.divide(BigDecimal.valueOf(dineInCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal takeAwayAov = takeAwayCount > 0
                ? takeAwayRevenue.divide(BigDecimal.valueOf(takeAwayCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Turnaround & SLA
        Double avgTurnaround = orderRepository.getAverageTurnaroundMinutesBetween(startDate, endDate);
        OrderRepository.TurnaroundSlaProjection sla = orderRepository.getTurnaroundSlaBetween(startDate, endDate);
        long fastCount = sla != null && sla.getFastCount() != null ? sla.getFastCount() : 0L;
        long standardCount = sla != null && sla.getStandardCount() != null ? sla.getStandardCount() : 0L;
        long delayedCount = sla != null && sla.getDelayedCount() != null ? sla.getDelayedCount() : 0L;
        long totalMeasured = fastCount + standardCount + delayedCount;
        Double onTimeRate = totalMeasured > 0
                ? Math.round(((double) (fastCount + standardCount) / totalMeasured) * 1000.0) / 10.0
                : 100.0;

        // Category breakdown
        List<OrderItemRepository.CategorySalesProjection> rawCat = orderItemRepository.getCategorySalesBetween(startDate, endDate);
        BigDecimal totalCatRev = rawCat.stream()
                .map(OrderItemRepository.CategorySalesProjection::getRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<BusinessInsightsResponse.CategoryShareDto> categoryShares = rawCat.stream()
                .map(c -> {
                    Double share = (totalCatRev.compareTo(BigDecimal.ZERO) > 0 && c.getRevenue() != null)
                            ? Math.round(c.getRevenue().multiply(BigDecimal.valueOf(100)).divide(totalCatRev, 2, RoundingMode.HALF_UP).doubleValue() * 10.0) / 10.0
                            : 0.0;
                    return BusinessInsightsResponse.CategoryShareDto.builder()
                            .categoryName(c.getCategoryName())
                            .unitsSold(c.getUnitsSold() != null ? c.getUnitsSold() : 0L)
                            .revenue(c.getRevenue() != null ? c.getRevenue() : BigDecimal.ZERO)
                            .percentageShare(share)
                            .build();
                }).toList();

        // Hourly traffic
        List<OrderRepository.HourlyTrafficProjection> rawHours = orderRepository.getHourlyTrafficBetween(startDate, endDate);
        List<BusinessInsightsResponse.HourlyTrafficDto> hourlyTraffic = rawHours.stream().map(h -> {
            int hr = h.getHour() != null ? h.getHour() : 0;
            String label = hr == 0 ? "12 AM" : hr < 12 ? hr + " AM" : hr == 12 ? "12 PM" : (hr - 12) + " PM";
            return BusinessInsightsResponse.HourlyTrafficDto.builder()
                    .hour(hr)
                    .label(label)
                    .orderCount(h.getOrderCount() != null ? h.getOrderCount() : 0L)
                    .revenue(h.getRevenue() != null ? h.getRevenue() : BigDecimal.ZERO)
                    .build();
        }).toList();

        // Payment splits
        List<PaymentRepository.PaymentMethodProjection> rawPay = paymentRepository.getPaymentMethodsBetween(startDate, endDate);
        BigDecimal totalPayAmount = rawPay.stream()
                .map(PaymentRepository.PaymentMethodProjection::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<BusinessInsightsResponse.PaymentSplitDto> paymentSplits = rawPay.stream().map(p -> {
            Double share = (totalPayAmount.compareTo(BigDecimal.ZERO) > 0 && p.getTotalAmount() != null)
                    ? Math.round(p.getTotalAmount().multiply(BigDecimal.valueOf(100)).divide(totalPayAmount, 2, RoundingMode.HALF_UP).doubleValue() * 10.0) / 10.0
                    : 0.0;
            return BusinessInsightsResponse.PaymentSplitDto.builder()
                    .method(p.getMethod())
                    .count(p.getCount() != null ? p.getCount() : 0L)
                    .totalAmount(p.getTotalAmount() != null ? p.getTotalAmount() : BigDecimal.ZERO)
                    .percentageShare(share)
                    .build();
        }).toList();

        // Top earners & Slow movers
        List<TopItemDto> topEarners = orderItemRepository.getFastMovingItemsBetween(startDate, endDate).stream()
                .limit(5)
                .map(p -> TopItemDto.builder()
                        .itemName(p.getItemName())
                        .totalSold(p.getTotalSold())
                        .totalRevenue(p.getTotalRevenue())
                        .build()).toList();

        List<TopItemDto> slowMovers = orderItemRepository.getSlowMovingItemsBetween(startDate, endDate).stream()
                .map(p -> TopItemDto.builder()
                        .itemName(p.getItemName())
                        .totalSold(p.getTotalSold() != null ? p.getTotalSold() : 0)
                        .totalRevenue(p.getTotalRevenue() != null ? p.getTotalRevenue() : BigDecimal.ZERO)
                        .build()).toList();

        // Table performance
        List<BusinessInsightsResponse.TablePerformanceDto> tableMetrics = orderRepository.getTablePerformanceBetween(startDate, endDate).stream()
                .map(t -> BusinessInsightsResponse.TablePerformanceDto.builder()
                        .tableNumber(t.getTableNumber())
                        .orderCount(t.getOrderCount() != null ? t.getOrderCount() : 0L)
                        .totalRevenue(t.getTotalRevenue() != null ? t.getTotalRevenue() : BigDecimal.ZERO)
                        .build()).toList();

        return BusinessInsightsResponse.builder()
                .grossSales(grossSales)
                .netSales(netSales)
                .totalDiscounts(totalDiscounts)
                .discountRatePercent(discountRate)
                .totalOrders(totalOrders)
                .billedOrders(billedOrders)
                .cancelledOrders(cancelledOrders)
                .overallAov(overallAov)
                .dineInCount(dineInCount)
                .dineInRevenue(dineInRevenue)
                .dineInAov(dineInAov)
                .takeAwayCount(takeAwayCount)
                .takeAwayRevenue(takeAwayRevenue)
                .takeAwayAov(takeAwayAov)
                .averageTurnaroundMinutes(avgTurnaround != null ? avgTurnaround : 0.0)
                .fastServiceCount(fastCount)
                .standardServiceCount(standardCount)
                .delayedServiceCount(delayedCount)
                .onTimeServiceRate(onTimeRate)
                .categoryShares(categoryShares)
                .hourlyTraffic(hourlyTraffic)
                .paymentSplits(paymentSplits)
                .topEarners(topEarners)
                .slowMovers(slowMovers)
                .tableMetrics(tableMetrics)
                .build();
    }
}