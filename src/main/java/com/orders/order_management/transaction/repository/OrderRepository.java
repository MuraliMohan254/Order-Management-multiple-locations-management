package com.orders.order_management.transaction.repository;


import com.orders.order_management.core.enums.OrderStatus;
import com.orders.order_management.core.enums.OrderType;
import com.orders.order_management.transaction.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.lang.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {
    
        @EntityGraph(attributePaths = {"items", "table", "outlet"})
    Page<Order> findAll(@Nullable Specification<Order> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"items", "table", "outlet"})
    Optional<Order> findByTableIdAndStatusIn(UUID tableId, List<OrderStatus> statuses);

    @EntityGraph(attributePaths = {"items", "table", "outlet"})
    Optional<Order> findById(UUID id);

    @Query("SELECT COALESCE(SUM(o.grandTotal), 0) FROM Order o WHERE o.status IN ('BILLED', 'PAID') AND o.createdAt >= :startDate AND o.createdAt <= :endDate AND (:outletId IS NULL OR o.outlet.id = :outletId)")
    BigDecimal calculateTotalSales(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate, @Param("outletId") String outletId);

    default BigDecimal calculateTotalSales(Instant startDate, Instant endDate) {
        return calculateTotalSales(startDate, endDate, null);
    }

    @Query(value = """
    SELECT COALESCE(AVG(EXTRACT(EPOCH FROM (updated_at - created_at)) / 60), 0)
    FROM orders 
    WHERE (status IN ('BILLED', 'PAID') OR kitchen_status = 'SERVED' OR status = 'SERVED')
    AND created_at >= :startDate AND created_at <= :endDate
    AND updated_at > created_at
    AND (CAST(:outletId AS VARCHAR) IS NULL OR outlet_id = CAST(:outletId AS VARCHAR))
""", nativeQuery = true)
    Double getAverageTurnaroundMinutesBetween(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate, @Param("outletId") String outletId);

    default Double getAverageTurnaroundMinutesBetween(Instant startDate, Instant endDate) {
        return getAverageTurnaroundMinutesBetween(startDate, endDate, null);
    }

    @Query(value = """
    SELECT COALESCE(AVG(EXTRACT(EPOCH FROM (updated_at - created_at)) / 60), 0)
    FROM orders 
    WHERE status IN ('BILLED', 'PAID') 
    AND created_at >= CURRENT_DATE - INTERVAL '30 days'
    AND updated_at > created_at
    AND (CAST(:outletId AS VARCHAR) IS NULL OR outlet_id = CAST(:outletId AS VARCHAR))
""", nativeQuery = true)
    Double getAverageTurnaroundTimeMinutesLast30Days(@Param("outletId") String outletId);

    default Double getAverageTurnaroundTimeMinutesLast30Days() {
        return getAverageTurnaroundTimeMinutesLast30Days(null);
    }

    List<Order> findByStatusIn(List<OrderStatus> statuses);

    @EntityGraph(attributePaths = {"items", "table", "outlet"})
    @Query("SELECT o FROM Order o WHERE o.status IN :statuses AND (o.kitchenStatus IS NULL OR (o.kitchenStatus != 'SERVED' AND o.kitchenStatus != 'CANCELLED')) ORDER BY o.createdAt ASC")
    List<Order> findActiveOrdersByStatusIn(@Param("statuses") List<OrderStatus> statuses);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.createdAt >= :startDate AND o.createdAt <= :endDate AND (:outletId IS NULL OR o.outlet.id = :outletId)")
    long countOrdersBetween(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate, @Param("outletId") String outletId);

    default long countOrdersBetween(Instant startDate, Instant endDate) {
        return countOrdersBetween(startDate, endDate, null);
    }

    @Query("SELECT COUNT(o) FROM Order o WHERE o.status IN ('BILLED', 'PAID') AND o.createdAt >= :startDate AND o.createdAt <= :endDate AND (:outletId IS NULL OR o.outlet.id = :outletId)")
    long countBilledOrdersBetween(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate, @Param("outletId") String outletId);

    default long countBilledOrdersBetween(Instant startDate, Instant endDate) {
        return countBilledOrdersBetween(startDate, endDate, null);
    }

    @Query("SELECT COUNT(o) FROM Order o WHERE o.orderType = :orderType AND o.createdAt >= :startDate AND o.createdAt <= :endDate AND (:outletId IS NULL OR o.outlet.id = :outletId)")
    long countByOrderTypeBetween(@Param("orderType") OrderType orderType, @Param("startDate") Instant startDate, @Param("endDate") Instant endDate, @Param("outletId") String outletId);

    default long countByOrderTypeBetween(OrderType orderType, Instant startDate, Instant endDate) {
        return countByOrderTypeBetween(orderType, startDate, endDate, null);
    }

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.table LEFT JOIN FETCH o.outlet WHERE o.createdAt >= :startDate AND o.createdAt <= :endDate AND (:outletId IS NULL OR o.outlet.id = :outletId) ORDER BY o.createdAt DESC")
    List<Order> findOrdersBetween(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate, @Param("outletId") String outletId);

    default List<Order> findOrdersBetween(Instant startDate, Instant endDate) {
        return findOrdersBetween(startDate, endDate, null);
    }

    @Query(value = """
        SELECT EXTRACT(HOUR FROM created_at)::int as hour, COUNT(id) as orderCount, COALESCE(SUM(grand_total), 0) as revenue
        FROM orders
        WHERE status IN ('BILLED', 'PAID')
        AND created_at >= :startDate AND created_at <= :endDate
        AND (CAST(:outletId AS VARCHAR) IS NULL OR outlet_id = CAST(:outletId AS VARCHAR))
        GROUP BY EXTRACT(HOUR FROM created_at)::int
        ORDER BY hour ASC
    """, nativeQuery = true)
    List<HourlyTrafficProjection> getHourlyTrafficBetween(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate, @Param("outletId") String outletId);

    default List<HourlyTrafficProjection> getHourlyTrafficBetween(Instant startDate, Instant endDate) {
        return getHourlyTrafficBetween(startDate, endDate, null);
    }

    interface HourlyTrafficProjection {
        Integer getHour();
        Long getOrderCount();
        BigDecimal getRevenue();
    }

    @Query(value = """
        SELECT t.table_number as tableNumber, COUNT(o.id) as orderCount, COALESCE(SUM(o.grand_total), 0) as totalRevenue
        FROM orders o
        JOIN restaurant_tables t ON o.table_id = t.id
        WHERE o.status IN ('BILLED', 'PAID')
        AND o.created_at >= :startDate AND o.created_at <= :endDate
        AND (CAST(:outletId AS VARCHAR) IS NULL OR o.outlet_id = CAST(:outletId AS VARCHAR))
        GROUP BY t.table_number
        ORDER BY totalRevenue DESC
        LIMIT 10
    """, nativeQuery = true)
    List<TablePerformanceProjection> getTablePerformanceBetween(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate, @Param("outletId") String outletId);

    default List<TablePerformanceProjection> getTablePerformanceBetween(Instant startDate, Instant endDate) {
        return getTablePerformanceBetween(startDate, endDate, null);
    }

    interface TablePerformanceProjection {
        String getTableNumber();
        Long getOrderCount();
        BigDecimal getTotalRevenue();
    }

    @Query("SELECT COALESCE(SUM(o.discountAmount), 0) FROM Order o WHERE o.status IN ('BILLED', 'PAID') AND o.createdAt >= :startDate AND o.createdAt <= :endDate AND (:outletId IS NULL OR o.outlet.id = :outletId)")
    BigDecimal calculateTotalDiscounts(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate, @Param("outletId") String outletId);

    default BigDecimal calculateTotalDiscounts(Instant startDate, Instant endDate) {
        return calculateTotalDiscounts(startDate, endDate, null);
    }

    @Query("SELECT COUNT(o) FROM Order o WHERE o.status = 'CANCELLED' AND o.createdAt >= :startDate AND o.createdAt <= :endDate AND (:outletId IS NULL OR o.outlet.id = :outletId)")
    long countCancelledOrdersBetween(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate, @Param("outletId") String outletId);

    default long countCancelledOrdersBetween(Instant startDate, Instant endDate) {
        return countCancelledOrdersBetween(startDate, endDate, null);
    }

    @Query("SELECT COALESCE(SUM(o.grandTotal), 0) FROM Order o WHERE o.status IN ('BILLED', 'PAID') AND o.orderType = :orderType AND o.createdAt >= :startDate AND o.createdAt <= :endDate AND (:outletId IS NULL OR o.outlet.id = :outletId)")
    BigDecimal calculateSalesByOrderType(@Param("orderType") OrderType orderType, @Param("startDate") Instant startDate, @Param("endDate") Instant endDate, @Param("outletId") String outletId);

    default BigDecimal calculateSalesByOrderType(OrderType orderType, Instant startDate, Instant endDate) {
        return calculateSalesByOrderType(orderType, startDate, endDate, null);
    }

    @Query(value = """
        SELECT 
            COALESCE(COUNT(CASE WHEN (EXTRACT(EPOCH FROM (updated_at - created_at)) / 60) <= 15 THEN 1 END), 0) as fastCount,
            COALESCE(COUNT(CASE WHEN (EXTRACT(EPOCH FROM (updated_at - created_at)) / 60) > 15 AND (EXTRACT(EPOCH FROM (updated_at - created_at)) / 60) <= 25 THEN 1 END), 0) as standardCount,
            COALESCE(COUNT(CASE WHEN (EXTRACT(EPOCH FROM (updated_at - created_at)) / 60) > 25 THEN 1 END), 0) as delayedCount
        FROM orders
        WHERE (status IN ('BILLED', 'PAID') OR kitchen_status = 'SERVED' OR status = 'SERVED')
        AND created_at >= :startDate AND created_at <= :endDate
        AND updated_at > created_at
        AND (CAST(:outletId AS VARCHAR) IS NULL OR outlet_id = CAST(:outletId AS VARCHAR))
    """, nativeQuery = true)
    TurnaroundSlaProjection getTurnaroundSlaBetween(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate, @Param("outletId") String outletId);

    default TurnaroundSlaProjection getTurnaroundSlaBetween(Instant startDate, Instant endDate) {
        return getTurnaroundSlaBetween(startDate, endDate, null);
    }

    interface TurnaroundSlaProjection {
        Long getFastCount();
        Long getStandardCount();
        Long getDelayedCount();
    }
}