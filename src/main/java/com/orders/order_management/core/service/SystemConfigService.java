package com.orders.order_management.core.service;

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

    @PostConstruct
    public void initDefaultConfig() {
        if (repository.count() == 0) {
            SystemConfig config = SystemConfig.builder()
                    .id(1L)
                    .kitchenWorkflowEnabled(true)
                    .taxRate(new BigDecimal("0.05"))
                    .taxName("GST")
                    .defaultDiscountPercentage(BigDecimal.ZERO)
                    .build();
            repository.save(config);
        }
    }

    public SystemConfig getConfig() {
        return repository.findById(1L).orElseGet(() -> {
            SystemConfig config = SystemConfig.builder()
                    .id(1L)
                    .kitchenWorkflowEnabled(true)
                    .taxRate(new BigDecimal("0.05"))
                    .taxName("GST")
                    .defaultDiscountPercentage(BigDecimal.ZERO)
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
        return repository.save(config);
    }
}
