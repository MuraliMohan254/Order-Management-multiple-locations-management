package com.orders.order_management.core.service;

import com.orders.order_management.core.entity.Outlet;
import com.orders.order_management.core.entity.SystemConfig;
import com.orders.order_management.core.repository.SystemConfigRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private final SystemConfigRepository repository;

    private Outlet createDefaultOutletRef() {
        Outlet outlet = new Outlet();
        outlet.setId("default-outlet-123");
        return outlet;
    }

    @PostConstruct
    public void initDefaultConfig() {
        if (repository.count() == 0) {
            SystemConfig config = SystemConfig.builder()
                    .id(1L)
                    .outlet(createDefaultOutletRef())
                    .kitchenWorkflowEnabled(true)
                    .taxRate(new BigDecimal("0.05"))
                    .taxName("GST")
                    .defaultDiscountPercentage(BigDecimal.ZERO)
                    .thermalPrintingEnabled(false)
                    .thermalPaperSize("80mm")
                    .printerName(null)
                    .restaurantName("WildBar")
                    .build();
            repository.save(config);
        }
    }

    public SystemConfig getConfig() {
        return repository.findById(1L).orElseGet(() -> {
            SystemConfig config = SystemConfig.builder()
                    .id(1L)
                    .outlet(createDefaultOutletRef())
                    .kitchenWorkflowEnabled(true)
                    .taxRate(new BigDecimal("0.05"))
                    .taxName("GST")
                    .defaultDiscountPercentage(BigDecimal.ZERO)
                    .thermalPrintingEnabled(false)
                    .thermalPaperSize("80mm")
                    .printerName(null)
                    .restaurantName("WildBar")
                    .build();
            return repository.save(config);
        });
    }

    public SystemConfig updateConfig(SystemConfig newConfig) {
        SystemConfig config = getConfig();
        config.setKitchenWorkflowEnabled(newConfig.isKitchenWorkflowEnabled());
        config.setTaxRate(newConfig.getTaxRate());
        config.setTaxName(newConfig.getTaxName());
        config.setDefaultDiscountPercentage(newConfig.getDefaultDiscountPercentage());
        config.setThermalPrintingEnabled(newConfig.isThermalPrintingEnabled());
        config.setThermalPaperSize(newConfig.getThermalPaperSize());
        config.setPrinterName(newConfig.getPrinterName());
        if (newConfig.getRestaurantName() != null && !newConfig.getRestaurantName().isBlank()) {
            config.setRestaurantName(newConfig.getRestaurantName());
        }
        return repository.save(config);
    }
}
