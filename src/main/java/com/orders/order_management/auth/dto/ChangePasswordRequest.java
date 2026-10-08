package com.orders.order_management.auth.dto;

import lombok.Data;

@Data
public class ChangePasswordRequest {
    private String newPassword;
}
