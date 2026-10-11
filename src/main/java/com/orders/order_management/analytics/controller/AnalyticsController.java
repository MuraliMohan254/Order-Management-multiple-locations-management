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
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(required = false) String outletId) {
        return ResponseEntity.ok(analyticsService.getSalesDashboard(startDate, endDate, outletId));
    }

    @GetMapping("/business-insights")
    public ResponseEntity<BusinessInsightsResponse> getBusinessInsights(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(required = false) String outletId) {
        return ResponseEntity.ok(analyticsService.getBusinessInsights(startDate, endDate, outletId));
    }

    @GetMapping("/reports/summary/pdf")
    public ResponseEntity<byte[]> downloadSalesSummaryPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(required = false) String outletId) {
        
        Instant start = startDate != null ? startDate : Instant.now().minus(30, ChronoUnit.DAYS);
        Instant end = endDate != null ? endDate : Instant.now();

        byte[] pdfReport = reportService.generateSalesSummaryPdf(start, end, outletId);

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
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(required = false) String outletId) {
        
        Instant start = startDate != null ? startDate : Instant.now().minus(30, ChronoUnit.DAYS);
        Instant end = endDate != null ? endDate : Instant.now();

        byte[] pdfReport = reportService.generateSalesReportPdf(start, end, outletId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("inline", "WildBar_Sales_Report.pdf");

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfReport);
    }

    @GetMapping("/reports/multi-outlet/daily/pdf")
    public ResponseEntity<byte[]> downloadDailyOutletSalesReportPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(required = false) String outletId) {
        
        Instant start = startDate != null ? startDate : Instant.now().minus(30, ChronoUnit.DAYS);
        Instant end = endDate != null ? endDate : Instant.now();

        byte[] pdfReport = reportService.generateDailyOutletSalesReportPdf(start, end, outletId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("inline", "WildBar_Multi_Outlet_Daily_Report.pdf");

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfReport);
    }

    @GetMapping("/reports/multi-outlet/monthly/pdf")
    public ResponseEntity<byte[]> downloadMonthlyOutletSalesReportPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(required = false) String outletId) {
        
        Instant start = startDate != null ? startDate : Instant.now().minus(180, ChronoUnit.DAYS);
        Instant end = endDate != null ? endDate : Instant.now();

        byte[] pdfReport = reportService.generateMonthlyOutletSalesReportPdf(start, end, outletId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        String name = reportService.getRestaurantName();
        headers.setContentDispositionFormData("inline", name + "_Multi_Outlet_Monthly_Report.pdf");

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfReport);
    }

    // ==========================================
    // EXCEL (XLSX) REPORT ENDPOINTS
    // ==========================================

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    @GetMapping("/reports/summary/xlsx")
    public ResponseEntity<byte[]> downloadSalesSummaryXlsx(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(required = false) String outletId) {
        
        Instant start = startDate != null ? startDate : Instant.now().minus(30, ChronoUnit.DAYS);
        Instant end = endDate != null ? endDate : Instant.now();

        byte[] xlsxReport = reportService.generateSalesSummaryXlsx(start, end, outletId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(XLSX_MEDIA_TYPE);
        String name = reportService.getRestaurantName();
        headers.setContentDispositionFormData("attachment", name + "_Sales_Summary_Report.xlsx");

        return ResponseEntity.ok()
                .headers(headers)
                .body(xlsxReport);
    }

    @GetMapping("/reports/sales/xlsx")
    public ResponseEntity<byte[]> downloadSalesReportXlsx(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(required = false) String outletId) {
        
        Instant start = startDate != null ? startDate : Instant.now().minus(30, ChronoUnit.DAYS);
        Instant end = endDate != null ? endDate : Instant.now();

        byte[] xlsxReport = reportService.generateSalesReportXlsx(start, end, outletId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(XLSX_MEDIA_TYPE);
        String name = reportService.getRestaurantName();
        headers.setContentDispositionFormData("attachment", name + "_Detailed_Transactions_Report.xlsx");

        return ResponseEntity.ok()
                .headers(headers)
                .body(xlsxReport);
    }

    @GetMapping("/reports/multi-outlet/daily/xlsx")
    public ResponseEntity<byte[]> downloadDailyOutletSalesReportXlsx(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(required = false) String outletId) {
        
        Instant start = startDate != null ? startDate : Instant.now().minus(30, ChronoUnit.DAYS);
        Instant end = endDate != null ? endDate : Instant.now();

        byte[] xlsxReport = reportService.generateDailyOutletSalesReportXlsx(start, end, outletId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(XLSX_MEDIA_TYPE);
        String name = reportService.getRestaurantName();
        headers.setContentDispositionFormData("attachment", name + "_Multi_Outlet_Daily_Report.xlsx");

        return ResponseEntity.ok()
                .headers(headers)
                .body(xlsxReport);
    }

    @GetMapping("/reports/multi-outlet/monthly/xlsx")
    public ResponseEntity<byte[]> downloadMonthlyOutletSalesReportXlsx(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(required = false) String outletId) {
        
        Instant start = startDate != null ? startDate : Instant.now().minus(180, ChronoUnit.DAYS);
        Instant end = endDate != null ? endDate : Instant.now();

        byte[] xlsxReport = reportService.generateMonthlyOutletSalesReportXlsx(start, end, outletId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(XLSX_MEDIA_TYPE);
        String name = reportService.getRestaurantName();
        headers.setContentDispositionFormData("attachment", name + "_Multi_Outlet_Monthly_Report.xlsx");

        return ResponseEntity.ok()
                .headers(headers)
                .body(xlsxReport);
    }
}
