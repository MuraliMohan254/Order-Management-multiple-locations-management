package com.orders.order_management.transaction.repository;


import com.orders.order_management.transaction.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    @Query(value = """
        SELECT p.payment_method as method, COUNT(p.id) as count, COALESCE(SUM(p.amount_paid), 0) as totalAmount
        FROM payments p
        JOIN orders o ON p.order_id = o.id
        WHERE (o.status IN ('BILLED', 'PAID') OR p.payment_status = 'SUCCESS')
        AND p.created_at >= :startDate AND p.created_at <= :endDate
        AND (CAST(:outletId AS VARCHAR) IS NULL OR p.outlet_id = CAST(:outletId AS VARCHAR) OR o.outlet_id = CAST(:outletId AS VARCHAR))
        GROUP BY p.payment_method
        ORDER BY totalAmount DESC
    """, nativeQuery = true)
    List<PaymentMethodProjection> getPaymentMethodsBetween(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate, @Param("outletId") String outletId);

    default List<PaymentMethodProjection> getPaymentMethodsBetween(Instant startDate, Instant endDate) {
        return getPaymentMethodsBetween(startDate, endDate, null);
    }

    interface PaymentMethodProjection {
        String getMethod();
        Long getCount();
        java.math.BigDecimal getTotalAmount();
    }
}
