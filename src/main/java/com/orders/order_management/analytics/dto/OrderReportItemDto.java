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
public class OrderReportItemDto {
    private String orderNumber;
    private String orderType;
    private String tableName;
    private String status;
    private BigDecimal grandTotal;
    private String createdAtText;
}
