package com.orders.order_management.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutletMonitoringDto {
    private String id;
    private String name;
    private String code;
    private String address;
    private String contactNumber;
    private boolean active;
    private BigDecimal todaySales;
    private long todayOrders;
    private long activeOrders;
    private long totalTables;
    private long occupiedTables;
    private BigDecimal averageOrderValue;
}
