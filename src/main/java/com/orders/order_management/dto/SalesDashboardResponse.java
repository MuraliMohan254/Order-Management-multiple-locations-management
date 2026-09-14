package com.orders.order_management.dto;

import com.orders.order_management.repos.OrderItemRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
public class SalesDashboardResponse {
    private BigDecimal dailySales;
    private BigDecimal monthlySales;
    private BigDecimal yearlySales;
    private List<OrderItemRepository.FastMovingItemProjection> fastMovingItems;
}
