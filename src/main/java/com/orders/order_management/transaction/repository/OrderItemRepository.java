package com.orders.order_management.transaction.repository;


import com.orders.order_management.transaction.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query(value = """
        SELECT i.name as itemName, SUM(oi.quantity) as totalSold, SUM(oi.total_price) as totalRevenue
        FROM order_items oi
        JOIN items i ON oi.item_id = i.id
        JOIN orders o ON oi.order_id = o.id
        WHERE o.status = 'BILLED' 
        AND o.created_at >= :startDate AND o.created_at <= :endDate
        GROUP BY i.name
        ORDER BY totalSold DESC
        LIMIT 10
    """, nativeQuery = true)
    List<FastMovingItemWithRevenueProjection> getFastMovingItemsBetween(@Param("startDate") java.time.Instant startDate, @Param("endDate") java.time.Instant endDate);

    interface FastMovingItemWithRevenueProjection {
        String getItemName();
        Integer getTotalSold();
        java.math.BigDecimal getTotalRevenue();
    }

    @Query("""
    SELECT oi FROM OrderItem oi 
    JOIN FETCH oi.order o 
    JOIN FETCH oi.item i 
    WHERE o.status IN ('CONFIRMED', 'PREPARING', 'BILLED', 'PAID') AND (o.kitchenStatus IS NULL OR o.kitchenStatus != 'SERVED')
    ORDER BY oi.createdAt ASC
""")
    List<OrderItem> findActiveKitchenTickets();

    @Query(value = """
        SELECT c.name as categoryName, COALESCE(SUM(oi.quantity), 0) as unitsSold, COALESCE(SUM(oi.total_price), 0) as revenue
        FROM order_items oi
        JOIN items i ON oi.item_id = i.id
        JOIN categories c ON i.category_id = c.id
        JOIN orders o ON oi.order_id = o.id
        WHERE o.status = 'BILLED'
        AND o.created_at >= :startDate AND o.created_at <= :endDate
        GROUP BY c.name
        ORDER BY revenue DESC
    """, nativeQuery = true)
    List<CategorySalesProjection> getCategorySalesBetween(@Param("startDate") java.time.Instant startDate, @Param("endDate") java.time.Instant endDate);

    interface CategorySalesProjection {
        String getCategoryName();
        Long getUnitsSold();
        java.math.BigDecimal getRevenue();
    }

    @Query(value = """
        SELECT i.name as itemName, COALESCE(SUM(oi.quantity), 0) as totalSold, COALESCE(SUM(oi.total_price), 0) as totalRevenue
        FROM items i
        LEFT JOIN order_items oi ON oi.item_id = i.id
        LEFT JOIN orders o ON oi.order_id = o.id AND o.status = 'BILLED' AND o.created_at >= :startDate AND o.created_at <= :endDate
        WHERE i.is_available = true
        GROUP BY i.name
        ORDER BY totalRevenue ASC
        LIMIT 5
    """, nativeQuery = true)
    List<FastMovingItemWithRevenueProjection> getSlowMovingItemsBetween(@Param("startDate") java.time.Instant startDate, @Param("endDate") java.time.Instant endDate);
}