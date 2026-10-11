package com.orders.order_management.core.repository;

import com.orders.order_management.core.entity.Outlet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

@Repository
public interface OutletRepository extends JpaRepository<Outlet, String> {
    List<Outlet> findByIsActiveTrue();

    @Query("""
        SELECT o FROM Outlet o
        WHERE o.isActive = true
          AND (:search IS NULL OR :search = '' 
               OR LOWER(o.name) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(o.code) LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY o.name ASC
    """)
    List<Outlet> searchActiveOutlets(@Param("search") String search, org.springframework.data.domain.Pageable pageable);

    @Query(value = """
        SELECT 
            o.id as id,
            o.name as name,
            o.code as code,
            o.address as address,
            o.contact_number as contactNumber,
            o.is_active as isActive,
            COALESCE(ord_today.sales, 0) as todaySales,
            COALESCE(ord_today.order_count, 0) as todayOrders,
            COALESCE(ord_active.active_count, 0) as activeOrders,
            COALESCE(tbl.total_tables, 0) as totalTables,
            COALESCE(tbl.occupied_tables, 0) as occupiedTables
        FROM outlets o
        LEFT JOIN (
            SELECT outlet_id, SUM(grand_total) as sales, COUNT(id) as order_count
            FROM orders
            WHERE status IN ('BILLED', 'PAID') AND created_at >= :startOfDay
            GROUP BY outlet_id
        ) ord_today ON ord_today.outlet_id = o.id
        LEFT JOIN (
            SELECT outlet_id, COUNT(id) as active_count
            FROM orders
            WHERE status IN ('CONFIRMED', 'PREPARING', 'READY', 'SERVED', 'SOFT_ORDER')
            GROUP BY outlet_id
        ) ord_active ON ord_active.outlet_id = o.id
        LEFT JOIN (
            SELECT outlet_id, 
                   COUNT(id) as total_tables, 
                   COUNT(CASE WHEN status = 'OCCUPIED' THEN 1 END) as occupied_tables
            FROM restaurant_tables
            GROUP BY outlet_id
        ) tbl ON tbl.outlet_id = o.id
        ORDER BY o.created_at ASC
    """, nativeQuery = true)
    List<OutletMonitoringProjection> getMonitoringSummary(@Param("startOfDay") Instant startOfDay);

    @Query(value = """
        SELECT 
            o.id as id,
            o.name as name,
            o.code as code,
            o.address as address,
            o.contact_number as contactNumber,
            o.is_active as isActive,
            COALESCE(ord_today.sales, 0) as todaySales,
            COALESCE(ord_today.order_count, 0) as todayOrders,
            COALESCE(ord_active.active_count, 0) as activeOrders,
            COALESCE(tbl.total_tables, 0) as totalTables,
            COALESCE(tbl.occupied_tables, 0) as occupiedTables
        FROM outlets o
        LEFT JOIN (
            SELECT outlet_id, SUM(grand_total) as sales, COUNT(id) as order_count
            FROM orders
            WHERE status IN ('BILLED', 'PAID') AND created_at >= :startOfDay
            GROUP BY outlet_id
        ) ord_today ON ord_today.outlet_id = o.id
        LEFT JOIN (
            SELECT outlet_id, COUNT(id) as active_count
            FROM orders
            WHERE status IN ('CONFIRMED', 'PREPARING', 'READY', 'SERVED', 'SOFT_ORDER')
            GROUP BY outlet_id
        ) ord_active ON ord_active.outlet_id = o.id
        LEFT JOIN (
            SELECT outlet_id, 
                   COUNT(id) as total_tables, 
                   COUNT(CASE WHEN status = 'OCCUPIED' THEN 1 END) as occupied_tables
            FROM restaurant_tables
            GROUP BY outlet_id
        ) tbl ON tbl.outlet_id = o.id
        WHERE (:search IS NULL OR LOWER(o.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(o.code) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(COALESCE(o.address, '')) LIKE LOWER(CONCAT('%', :search, '%')))
          AND (:activeStatus IS NULL OR o.is_active = :activeStatus)
        ORDER BY o.created_at ASC
    """, countQuery = """
        SELECT COUNT(o.id)
        FROM outlets o
        WHERE (:search IS NULL OR LOWER(o.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(o.code) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(COALESCE(o.address, '')) LIKE LOWER(CONCAT('%', :search, '%')))
          AND (:activeStatus IS NULL OR o.is_active = :activeStatus)
    """, nativeQuery = true)
    org.springframework.data.domain.Page<OutletMonitoringProjection> searchMonitoringSummary(
            @Param("startOfDay") Instant startOfDay,
            @Param("search") String search,
            @Param("activeStatus") Boolean activeStatus,
            org.springframework.data.domain.Pageable pageable);
}
