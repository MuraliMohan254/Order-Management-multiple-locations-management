package com.orders.order_management.repos;
import com.orders.order_management.entity.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ItemRepository extends JpaRepository<Item, UUID> {

    // Fetches only available items for a specific category
    List<Item> findByCategoryIdAndIsAvailableTrue(UUID categoryId);
    List<Item> findByCategoryId(UUID categoryId);
    // New: Search items within a specific category
    List<Item> findByCategoryIdAndNameContainingIgnoreCaseAndIsAvailableTrue(UUID categoryId, String name);

    // New (Highly Recommended): Global search across all available items
    List<Item> findByNameContainingIgnoreCaseAndIsAvailableTrue(String name);
    List<Item> findByIsAvailableTrue();
}