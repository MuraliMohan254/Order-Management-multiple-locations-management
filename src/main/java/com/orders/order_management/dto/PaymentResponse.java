package com.orders.order_management.dto;

import com.orders.order_management.enums.PaymentMethod;
import com.orders.order_management.enums.PaymentStatus;
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
}