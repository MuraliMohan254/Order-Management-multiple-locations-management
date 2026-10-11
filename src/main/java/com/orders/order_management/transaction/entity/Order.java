package com.orders.order_management.transaction.entity;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.orders.order_management.core.entity.BaseEntity;
import com.orders.order_management.core.enums.OrderStatus;
import com.orders.order_management.core.enums.OrderType;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import com.orders.order_management.core.entity.Outlet;
import lombok.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
@Entity
@FilterDef(name = "outletFilter", parameters = {@ParamDef(name = "outletId", type = String.class)})
@Filter(name = "outletFilter", condition = "outlet_id = :outletId")
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_status", columnList = "status"),
        @Index(name = "idx_orders_created_at", columnList = "created_at"),
        @Index(name = "idx_orders_status_created_at", columnList = "status, created_at"),
        @Index(name = "idx_orders_order_number", columnList = "order_number"),
        @Index(name = "idx_orders_table_id", columnList = "table_id"),
        @Index(name = "idx_orders_order_type", columnList = "order_type"),
        @Index(name = "idx_orders_kitchen_status", columnList = "kitchen_status"),
        @Index(name = "idx_orders_outlet_created_at_status", columnList = "outlet_id, created_at, status"),
        @Index(name = "idx_orders_outlet_status_kitchen", columnList = "outlet_id, status, kitchen_status"),
        @Index(name = "idx_orders_table_status", columnList = "table_id, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order extends BaseEntity {

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "outlet_id")
    private Outlet outlet;

    @Column(name = "order_number", nullable = false, unique = true, length = 50)
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 20)
    private OrderType orderType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Builder.Default
    @Column(name = "kitchen_status", length = 20)
    private String kitchenStatus = "PENDING";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_id")
    private RestaurantTable table;

    @Builder.Default
    @Column(name = "sub_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal subTotal = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "tax_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "discount_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "grand_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal grandTotal = BigDecimal.ZERO;

    @Builder.Default
    @org.hibernate.annotations.BatchSize(size = 50)
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Payment> payments = new ArrayList<>();

    public void addOrderItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    @PrePersist
    public void onPrePersistTenant() {
        if (this.outlet == null) {
            String currentOutletId = com.orders.order_management.core.security.SecurityUtils.getCurrentOutletId();
            if (currentOutletId != null) {
                Outlet o = new Outlet();
                o.setId(currentOutletId);
                this.outlet = o;
            }
        }
    }
}


