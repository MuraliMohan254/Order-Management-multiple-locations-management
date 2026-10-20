package com.orders.order_management.transaction.entity;

import com.orders.order_management.core.entity.BaseEntity;
import com.orders.order_management.menu.entity.Item;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "order_items", indexes = {
    @Index(name = "idx_order_items_order_id", columnList = "order_id"),
    @Index(name = "idx_order_items_item_id", columnList = "item_id"),
    @Index(name = "idx_order_items_item_order", columnList = "item_id, order_id"),
    @Index(name = "idx_order_items_order_batch", columnList = "order_id, batch_number")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "total_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalPrice;

    @Column(length = 255)
    private String notes;

    @Column(name = "batch_number", nullable = false)
    @Builder.Default
    private Integer batchNumber = 1;
}
