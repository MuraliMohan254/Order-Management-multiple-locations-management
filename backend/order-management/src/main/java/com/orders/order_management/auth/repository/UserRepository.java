package com.orders.order_management.auth.repository;

import com.orders.order_management.auth.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByUsername(String username);
    Optional<User> findByUsername(String username);

    @Query(value = "SELECT u FROM User u LEFT JOIN FETCH u.outlet WHERE " +
            "(:searchTerm IS NULL OR :searchTerm = '' OR LOWER(u.username) LIKE LOWER(CONCAT('%', :searchTerm, '%')))",
           countQuery = "SELECT COUNT(u) FROM User u WHERE " +
            "(:searchTerm IS NULL OR :searchTerm = '' OR LOWER(u.username) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<User> searchUsers(@Param("searchTerm") String searchTerm, Pageable pageable);
}
