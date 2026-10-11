package com.orders.order_management.transaction.dto;


import com.orders.order_management.core.enums.PaymentMethod;
import com.orders.order_management.core.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    private UUID id;
    private UUID orderId;
    private BigDecimal amountPaid;
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;
    private Long settledById;
    private String settledBy;
}