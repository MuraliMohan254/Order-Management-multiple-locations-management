package com.orders.order_management.menu.entity;


import com.orders.order_management.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}