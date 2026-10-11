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
    private String outletId;
    private String outletName;

    public TableResponse(UUID id, String tableNumber, UUID qrToken, String status) {
        this(id, tableNumber, qrToken, status, null, null);
    }
}