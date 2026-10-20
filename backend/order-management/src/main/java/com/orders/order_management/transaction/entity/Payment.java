package com.orders.order_management.transaction.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.orders.order_management.core.entity.BaseEntity;
import com.orders.order_management.core.enums.PaymentMethod;
import com.orders.order_management.core.enums.PaymentStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import com.orders.order_management.core.entity.Outlet;
import lombok.*;
import java.math.BigDecimal;

@Entity
@FilterDef(name = "outletFilter", parameters = {@ParamDef(name = "outletId", type = String.class)})
@Filter(name = "outletFilter", condition = "outlet_id = :outletId")
@Table(name = "payments", indexes = {
        @Index(name = "idx_payments_outlet_created_status", columnList = "outlet_id, created_at, payment_status"),
        @Index(name = "idx_payments_order_status", columnList = "order_id, payment_status"),
        @Index(name = "idx_payments_settled_by_id", columnList = "settled_by_id")
})


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment extends BaseEntity {

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "outlet_id")
    private Outlet outlet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "amount_paid", nullable = false, precision = 15, scale = 2)
    private BigDecimal amountPaid;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 50)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus;

    @Column(name = "settled_by", length = 100)
    private String settledBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settled_by_id")
    private com.orders.order_management.auth.entity.User settledByUser;

    @PrePersist
    public void onPrePersistTenant() {
        if (this.outlet == null) {
            if (this.order != null && this.order.getOutlet() != null) {
                this.outlet = this.order.getOutlet();
            } else {
                String currentOutletId = com.orders.order_management.core.security.SecurityUtils.getCurrentOutletId();
                if (currentOutletId != null) {
                    Outlet o = new Outlet();
                    o.setId(currentOutletId);
                    this.outlet = o;
                }
            }
        }
    }
}


