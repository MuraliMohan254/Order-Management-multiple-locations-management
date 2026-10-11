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
public class DailyOutletReportItemDto {
    private String dateGroup;
    private String dateSortKey;
    private String outletId;
    private String outletName;
    private String outletCode;
    private Long orderCount;
    private BigDecimal totalRevenue;
    private BigDecimal averageOrderValue;
}
