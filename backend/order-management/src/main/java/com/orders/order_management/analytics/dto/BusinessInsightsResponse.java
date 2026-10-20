package com.orders.order_management.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessInsightsResponse {

    // 1. Financial & Volume Summary
    private BigDecimal grossSales;
    private BigDecimal netSales;
    private BigDecimal totalDiscounts;
    private Double discountRatePercent;
    private long totalOrders;
    private long billedOrders;
    private long cancelledOrders;
    private BigDecimal overallAov;

    // 2. Channel Performance (Dine-in vs Takeaway)
    private long dineInCount;
    private BigDecimal dineInRevenue;
    private BigDecimal dineInAov;
    private long takeAwayCount;
    private BigDecimal takeAwayRevenue;
    private BigDecimal takeAwayAov;

    // 3. Kitchen & Speed of Service SLA
    private Double averageTurnaroundMinutes;
    private long fastServiceCount;      // <= 15 mins
    private long standardServiceCount;  // 15 - 25 mins
    private long delayedServiceCount;   // > 25 mins
    private Double onTimeServiceRate;   // % of orders <= 25 mins

    // 4. Detailed Breakdowns
    private List<CategoryShareDto> categoryShares;
    private List<HourlyTrafficDto> hourlyTraffic;
    private List<PaymentSplitDto> paymentSplits;
    private List<TopItemDto> topEarners;
    private List<TopItemDto> slowMovers;
    private List<TablePerformanceDto> tableMetrics;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryShareDto {
        private String categoryName;
        private BigDecimal revenue;
        private long unitsSold;
        private Double percentageShare;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HourlyTrafficDto {
        private int hour;
        private String label; // e.g. "12 PM", "1 PM"
        private long orderCount;
        private BigDecimal revenue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentSplitDto {
        private String method; // CASH, CARD, UPI
        private long count;
        private BigDecimal totalAmount;
        private Double percentageShare;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TablePerformanceDto {
        private String tableNumber;
        private long orderCount;
        private BigDecimal totalRevenue;
    }
}
