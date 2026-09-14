package com.orders.order_management.repos;
import com.orders.order_management.entity.Order;
import com.orders.order_management.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    // Updated to use the OrderStatus enum
    Optional<Order> findByTableIdAndStatusIn(UUID tableId, List<OrderStatus> statuses);

    @Query("SELECT COALESCE(SUM(o.grandTotal), 0) FROM Order o WHERE o.status = 'BILLED' AND o.createdAt >= :startDate AND o.createdAt <= :endDate")
    BigDecimal calculateTotalSales(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate);

    @Query(value = """
    SELECT AVG(EXTRACT(EPOCH FROM (updated_at - created_at)) / 60)
    FROM orders 
    WHERE status = 'BILLED' 
    AND created_at >= CURRENT_DATE - INTERVAL '30 days'
""", nativeQuery = true)
    Double getAverageTurnaroundTimeMinutesLast30Days();
}