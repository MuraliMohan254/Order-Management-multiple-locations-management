package com.orders.order_management.repos;

import com.orders.order_management.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    // Required for appending items and JasperReports generation
    List<OrderItem> findByOrderId(UUID orderId);

    // Required for the Analytics Dashboard
    @Query(value = """
        SELECT i.name as itemName, SUM(oi.quantity) as totalSold
        FROM order_items oi
        JOIN items i ON oi.item_id = i.id
        JOIN orders o ON oi.order_id = o.id
        WHERE o.status = 'BILLED' 
        AND o.created_at >= CURRENT_DATE - INTERVAL '30 days'
        GROUP BY i.name
        ORDER BY totalSold DESC
        LIMIT 10
    """, nativeQuery = true)
    List<FastMovingItemProjection> getFastMovingItemsLast30Days();

    // Spring Data JPA Projection to map the native query results
    interface FastMovingItemProjection {
        String getItemName();
        Integer getTotalSold();
    }

    @Query("""
    SELECT oi FROM OrderItem oi 
    JOIN FETCH oi.order o 
    JOIN FETCH oi.item i 
    WHERE o.status IN ('CONFIRMED', 'PREPARING')
    ORDER BY oi.createdAt ASC
""")
    List<OrderItem> findActiveKitchenTickets();
}