package com.orders.order_management.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data @AllArgsConstructor
public class AuthResponse {
    private String token;
    private String subscriptionStatus; // e.g., "ACTIVE", "INACTIVE"
    private String expiryDate;
    private String notifyDate;
}
