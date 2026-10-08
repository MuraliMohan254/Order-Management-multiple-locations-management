package com.orders.order_management.transaction.repository;


import com.orders.order_management.transaction.entity.RestaurantTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RestaurantTableRepository extends JpaRepository<RestaurantTable, UUID> {
    Optional<RestaurantTable> findByQrToken(UUID qrToken);
}
