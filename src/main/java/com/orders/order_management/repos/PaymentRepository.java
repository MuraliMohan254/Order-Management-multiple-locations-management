package com.orders.order_management.repos;

import com.orders.order_management.entity.Payment;
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
        WHERE o.status = 'BILLED'
        AND o.created_at >= :startDate AND o.created_at <= :endDate
        GROUP BY p.payment_method
        ORDER BY totalAmount DESC
    """, nativeQuery = true)
    List<PaymentMethodProjection> getPaymentMethodsBetween(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate);

    interface PaymentMethodProjection {
        String getMethod();
        Long getCount();
        java.math.BigDecimal getTotalAmount();
    }
}
