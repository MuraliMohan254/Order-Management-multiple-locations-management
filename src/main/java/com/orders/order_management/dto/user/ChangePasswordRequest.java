package com.orders.order_management.dto.user;

import lombok.Data;

@Data
public class ChangePasswordRequest {
    private String newPassword;
}
