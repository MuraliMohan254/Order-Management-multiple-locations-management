package com.orders.order_management.auth.dto;

import lombok.Data;

@Data
public class SubscriptionResponse {
    private String subscription; // "ACTIVE", "INACTIVE"
    private String expiry_date;
    private String notify_date;
}