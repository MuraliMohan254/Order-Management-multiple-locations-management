package com.orders.order_management.auth.dto;


import com.orders.order_management.core.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class OnboardUserRequest {
    @Schema(description = "Username of the new user", example = "john_doe", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;
    @Schema(description = "Password for the new user", example = "_!#jhhyr", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
    @Schema(description = "Role for the user", example = "SALES", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Role role;
}
