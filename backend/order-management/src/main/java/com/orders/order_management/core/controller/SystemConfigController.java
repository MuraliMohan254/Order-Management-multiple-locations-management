package com.orders.order_management.core.controller;

import com.orders.order_management.core.entity.SystemConfig;
import com.orders.order_management.core.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${api.prefix}/config")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigService configService;

    @GetMapping
    public ResponseEntity<SystemConfig> getConfig() {
        return ResponseEntity.ok(configService.getConfig());
    }

    @PutMapping
    public ResponseEntity<SystemConfig> updateConfig(@RequestBody SystemConfig config) {
        return ResponseEntity.ok(configService.updateConfig(config));
    }
}
