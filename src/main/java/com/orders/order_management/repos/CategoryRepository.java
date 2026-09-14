package com.orders.order_management.repos;

import com.orders.order_management.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    // Fetches only active categories, supporting the soft-delete pattern
    List<Category> findAllByIsActiveTrue();
}