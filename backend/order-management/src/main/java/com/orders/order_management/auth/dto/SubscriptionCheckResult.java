package com.orders.order_management.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SubscriptionCheckResult {
    private String subscription;
    private String expiry_date;
    private String notify_date;
    public boolean isExpired() {
        return "INACTIVE".equalsIgnoreCase(subscription);
    }
}