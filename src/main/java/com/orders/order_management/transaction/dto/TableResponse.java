package com.orders.order_management.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableResponse {
    private UUID id;
    private String tableNumber;
    private UUID qrToken;
    private String status;
}