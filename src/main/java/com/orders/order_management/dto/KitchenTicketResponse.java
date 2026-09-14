package com.orders.order_management.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class KitchenTicketResponse {
    private UUID orderItemId;
    private String orderNumber;
    private String tableName;
    private String itemName;
    private Integer quantity;
    private String notes;
    private Instant orderTime;
}