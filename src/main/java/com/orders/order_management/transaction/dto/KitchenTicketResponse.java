package com.orders.order_management.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KitchenTicketResponse {
    private UUID orderItemId;
    private UUID orderId;
    private String orderNumber;
    private String tableName;
    private String itemName;
    private Integer quantity;
    private String notes;
    private Instant orderTime;
    private String status;
}