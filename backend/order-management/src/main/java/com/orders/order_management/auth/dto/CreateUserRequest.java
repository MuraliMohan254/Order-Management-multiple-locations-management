package com.orders.order_management.auth.dto;

import com.orders.order_management.core.enums.Role;
import lombok.Data;
import java.util.Set;

@Data
public class CreateUserRequest {

    private String outletId;
    private String username;
    private String password;
    private Set<Role> roles;
}

