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
public class MonthlyOutletReportItemDto {
    private String monthGroup;
    private String monthSortKey;
    private String outletId;
    private String outletName;
    private String outletCode;
    private Long orderCount;
    private BigDecimal totalRevenue;
    private BigDecimal averageOrderValue;
}
