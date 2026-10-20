package com.orders.order_management.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class AuthRequest {
    @Schema(description = "Username of the user", example = "john_doe", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;
    @Schema(description = "Password of the user", example = "P@ssw0rd123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
}
