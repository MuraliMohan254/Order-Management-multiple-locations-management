package com.orders.order_management.analytics.dto;


import com.orders.order_management.transaction.dto.OrderResponse;
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
public class SalesDashboardResponse {
    private BigDecimal dailySales;
    private BigDecimal monthlySales;
    private BigDecimal yearlySales;
    private BigDecimal periodSales;
    private long totalOrdersCount;
    private long billedOrdersCount;
    private long dineInOrdersCount;
    private long takeAwayOrdersCount;
    private BigDecimal averageOrderValue;
    private Double averageTurnaroundMinutes;
    private List<TopItemDto> fastMovingItems;
    private List<OrderResponse> recentOrders;
}
