package com.orders.order_management.auth.dto;

import com.orders.order_management.core.enums.Role;
import lombok.Data;
import java.util.Set;

@Data
public class UpdateUserRequest {

    private String outletId;
    private String username;
    private Set<Role> roles;
}

