package com.orders.order_management.controller;

import com.orders.order_management.dto.SalesDashboardResponse;
import com.orders.order_management.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${api.prefix}/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/dashboard")
    public ResponseEntity<SalesDashboardResponse> getDashboardMetrics() {
        return ResponseEntity.ok(analyticsService.getSalesDashboard());
    }
}
