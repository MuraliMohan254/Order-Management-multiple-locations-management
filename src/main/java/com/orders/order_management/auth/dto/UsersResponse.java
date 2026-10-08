package com.orders.order_management.auth.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class UsersResponse {
    private int userId;
    private String userName;
    private String role;
    private boolean active;
    private Instant createdAt;
    private Instant lastLogin;
}

