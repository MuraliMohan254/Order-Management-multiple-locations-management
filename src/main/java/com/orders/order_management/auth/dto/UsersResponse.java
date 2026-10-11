package com.orders.order_management.auth.dto;

import com.orders.order_management.core.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsersResponse {

    private String outletId;
    private String outletName;
    private Long id;
    private String username;
    private Set<Role> roles;
    private boolean active;
    private Instant createdAt;
    private Instant lastLogin;
}
