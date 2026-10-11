package com.orders.order_management.core.repository;

import java.math.BigDecimal;

public interface OutletMonitoringProjection {
    String getId();
    String getName();
    String getCode();
    String getAddress();
    String getContactNumber();
    Boolean getIsActive();
    BigDecimal getTodaySales();
    Long getTodayOrders();
    Long getActiveOrders();
    Long getTotalTables();
    Long getOccupiedTables();
}
