package com.orders.order_management.core.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "system_config")
@FilterDef(name = "outletFilter", parameters = {@ParamDef(name = "outletId", type = String.class)})
@Filter(name = "outletFilter", condition = "outlet_id = :outletId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class SystemConfig {

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "outlet_id")
    private Outlet outlet;

    @JsonProperty("outletId")
    public String getOutletId() {
        if (outlet == null) return null;
        if (outlet instanceof org.hibernate.proxy.HibernateProxy proxy) {
            return (String) proxy.getHibernateLazyInitializer().getIdentifier();
        }
        return outlet.getId();
    }

    @Id
    private Long id;

    private boolean kitchenWorkflowEnabled;

    private BigDecimal taxRate; // E.g., 0.05 for 5%

    private String taxName;

    private BigDecimal defaultDiscountPercentage; // E.g., 0.00
    
    @Builder.Default
    private boolean thermalPrintingEnabled = false;
    
    @Builder.Default
    private String thermalPaperSize = "80mm"; // 80mm or 58mm
    
    private String printerName; // null means default printer

    @Builder.Default
    @Column(name = "restaurant_name")
    private String restaurantName = "WildBar";

    @Column(name = "gst_number", length = 50)
    private String gstNumber;

    @Builder.Default
    @Column(name = "printer_ip", length = 50)
    private String printerIp = "192.168.31.53";

    @Builder.Default
    @Column(name = "printer_port")
    private Integer printerPort = 9100;

    @Builder.Default
    @Column(name = "quick_bill_print_receipt")
    private Boolean quickBillPrintReceipt = true;

    @PrePersist
    public void onPrePersistTenant() {
        if (this.outlet == null) {
            String currentOutletId = com.orders.order_management.core.security.SecurityUtils.getCurrentOutletId();
            Outlet o = new Outlet();
            o.setId(currentOutletId != null ? currentOutletId : "default-outlet-123");
            this.outlet = o;
        }
    }
}
