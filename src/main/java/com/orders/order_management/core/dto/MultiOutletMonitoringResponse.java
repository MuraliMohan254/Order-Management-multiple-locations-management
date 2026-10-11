package com.orders.order_management.core.dto;

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
public class MultiOutletMonitoringResponse {
    private BigDecimal consolidatedTodaySales;
    private long consolidatedTodayOrders;
    private long consolidatedActiveOrders;
    private long consolidatedOccupiedTables;
    private long consolidatedTotalTables;
    private int totalOutlets;
    private int activeOutlets;
    private List<OutletMonitoringDto> outlets;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private boolean last;
}
