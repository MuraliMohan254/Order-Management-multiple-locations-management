package com.orders.order_management.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopItemDto {
    private String itemName;
    private Integer totalSold;
    private BigDecimal totalRevenue;
}
