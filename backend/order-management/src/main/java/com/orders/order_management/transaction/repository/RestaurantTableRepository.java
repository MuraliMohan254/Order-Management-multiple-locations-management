package com.orders.order_management.transaction.repository;


import com.orders.order_management.transaction.entity.RestaurantTable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RestaurantTableRepository extends JpaRepository<RestaurantTable, UUID> {
    @EntityGraph(attributePaths = {"outlet"})
    List<RestaurantTable> findAll();

    @EntityGraph(attributePaths = {"outlet"})
    List<RestaurantTable> findByOutletId(String outletId);

    @EntityGraph(attributePaths = {"outlet"})
    Optional<RestaurantTable> findByQrToken(UUID qrToken);

    @EntityGraph(attributePaths = {"outlet"})
    Optional<RestaurantTable> findById(UUID id);
}
