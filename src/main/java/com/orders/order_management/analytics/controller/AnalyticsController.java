package com.orders.order_management.analytics.controller;


import com.orders.order_management.analytics.dto.BusinessInsightsResponse;
import com.orders.order_management.analytics.dto.SalesDashboardResponse;
import com.orders.order_management.analytics.service.AnalyticsService;
import com.orders.order_management.analytics.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@RestController
@RequestMapping("${api.prefix}/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final ReportService reportService;

    @GetMapping("/dashboard")
    public ResponseEntity<SalesDashboardResponse> getDashboardMetrics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate) {
        return ResponseEntity.ok(analyticsService.getSalesDashboard(startDate, endDate));
    }

    @GetMapping("/business-insights")
    public ResponseEntity<BusinessInsightsResponse> getBusinessInsights(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate) {
        return ResponseEntity.ok(analyticsService.getBusinessInsights(startDate, endDate));
    }

    @GetMapping("/reports/summary/pdf")
    public ResponseEntity<byte[]> downloadSalesSummaryPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate) {
        
        Instant start = startDate != null ? startDate : Instant.now().minus(30, ChronoUnit.DAYS);
        Instant end = endDate != null ? endDate : Instant.now();

        byte[] pdfReport = reportService.generateSalesSummaryPdf(start, end);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("filename", "sales_summary_report.pdf");

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfReport);
    }

    @GetMapping("/reports/sales/pdf")
    public ResponseEntity<byte[]> downloadSalesReportPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate) {
        
        Instant start = startDate != null ? startDate : Instant.now().minus(30, ChronoUnit.DAYS);
        Instant end = endDate != null ? endDate : Instant.now();

        byte[] pdfReport = reportService.generateSalesReportPdf(start, end);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("inline", "WildBar_Sales_Report.pdf");

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfReport);
    }
}
