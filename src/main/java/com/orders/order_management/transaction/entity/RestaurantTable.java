package com.orders.order_management.transaction.entity;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.orders.order_management.core.entity.BaseEntity;
import com.orders.order_management.core.enums.TableStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import com.orders.order_management.core.entity.Outlet;
import lombok.*;

import java.util.UUID;

@Entity
@FilterDef(name = "outletFilter", parameters = {@ParamDef(name = "outletId", type = String.class)})
@Filter(name = "outletFilter", condition = "outlet_id = :outletId")
@Table(name = "restaurant_tables", uniqueConstraints = {
        @UniqueConstraint(name = "uk_tables_number_outlet", columnNames = {"table_number", "outlet_id"})
}, indexes = {
        @Index(name = "idx_restaurant_tables_outlet_status", columnList = "outlet_id, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RestaurantTable extends BaseEntity {

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "outlet_id")
    private Outlet outlet;

    @Column(name = "table_number", nullable = false, length = 20)
    private String tableNumber;

    @Column(name = "qr_token", nullable = false, unique = true, updatable = false)
    private UUID qrToken;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 50)
    private TableStatus status = TableStatus.AVAILABLE;

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


