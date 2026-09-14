package com.orders.order_management.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.http.HttpStatusCode;

@Data
@AllArgsConstructor
public class OnboardUserResponse {
    private String message;
    private HttpStatusCode statusCode;
}
