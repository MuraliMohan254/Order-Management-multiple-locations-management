package com.orders.order_management.core.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "system_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemConfig {

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
}
