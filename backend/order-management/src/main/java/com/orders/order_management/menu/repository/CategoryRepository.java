package com.orders.order_management.menu.repository;


import com.orders.order_management.menu.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    // Fetches only active categories, supporting the soft-delete pattern
    List<Category> findAllByIsActiveTrue();
    List<Category> findAllByIsActive(boolean isActive);
    List<Category> findByNameContainingIgnoreCaseAndIsActive(String name, boolean isActive);
}