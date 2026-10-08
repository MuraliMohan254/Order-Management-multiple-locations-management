package com.orders.order_management.analytics.service;


import com.orders.order_management.analytics.dto.OrderReportItemDto;
import com.orders.order_management.core.enums.OrderType;
import com.orders.order_management.transaction.entity.Order;
import com.orders.order_management.transaction.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.design.*;
import net.sf.jasperreports.engine.type.HorizontalTextAlignEnum;
import net.sf.jasperreports.engine.type.VerticalTextAlignEnum;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final OrderRepository orderRepository;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm").withZone(ZoneId.systemDefault());
    private static volatile JasperReport cachedReport = null;

    public byte[] generateSalesReportPdf(Instant startDate, Instant endDate) {
        try {
            // 1. Fetch data
            List<Order> orders = orderRepository.findOrdersBetween(startDate, endDate);
            BigDecimal totalRevenue = orderRepository.calculateTotalSales(startDate, endDate);
            long totalOrders = orderRepository.countOrdersBetween(startDate, endDate);
            long dineInCount = orderRepository.countByOrderTypeBetween(OrderType.DINE_IN, startDate, endDate);
            long takeAwayCount = orderRepository.countByOrderTypeBetween(OrderType.TAKE_AWAY, startDate, endDate);

            // 2. Map to DTOs
            List<OrderReportItemDto> reportItems = orders.stream().map(o -> OrderReportItemDto.builder()
                    .orderNumber(o.getOrderNumber() != null ? o.getOrderNumber() : "N/A")
                    .orderType(o.getOrderType() != null ? o.getOrderType().name() : "N/A")
                    .tableName(o.getTable() != null ? o.getTable().getTableNumber() : "Takeaway")
                    .status(o.getStatus() != null ? o.getStatus().name() : "N/A")
                    .grandTotal(o.getGrandTotal() != null ? o.getGrandTotal() : (o.getSubTotal() != null ? o.getSubTotal() : BigDecimal.ZERO))
                    .createdAtText(o.getCreatedAt() != null ? DATE_FORMATTER.format(o.getCreatedAt()) : "N/A")
                    .build()).toList();

            // 3. Prepare Parameters
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("reportTitle", "Sales & Orders Performance Report");
            parameters.put("dateRangeText", DATE_FORMATTER.format(startDate) + " to " + DATE_FORMATTER.format(endDate));
            parameters.put("generatedAt", DATE_FORMATTER.format(Instant.now()));
            parameters.put("totalRevenueText", "INR " + (totalRevenue != null ? totalRevenue.toPlainString() : "0.00"));
            parameters.put("totalOrdersText", String.valueOf(totalOrders));
            parameters.put("dineInText", String.valueOf(dineInCount));
            parameters.put("takeAwayText", String.valueOf(takeAwayCount));

            // 4. Calculate High-level Totals (Today, Month)
            Instant startOfDay = java.time.LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
            BigDecimal todayTotal = orderRepository.calculateTotalSales(startOfDay, Instant.now());
            if (todayTotal == null) todayTotal = BigDecimal.ZERO;
            
            Instant startOfMonth = java.time.LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
            BigDecimal monthTotal = orderRepository.calculateTotalSales(startOfMonth, Instant.now());
            if (monthTotal == null) monthTotal = BigDecimal.ZERO;

            parameters.put("todayTotal", todayTotal);
            parameters.put("monthTotal", monthTotal);
            
            // Also need numeric totals for the new design
            parameters.put("totalRevenue", totalRevenue != null ? totalRevenue : BigDecimal.ZERO);
            parameters.put("totalOrders", totalOrders);
            parameters.put("dineInCount", dineInCount);
            parameters.put("takeAwayCount", takeAwayCount);

            // 5. Load Pre-compiled Report Design
            JasperReport jasperReport = loadCompiledReport();

            // 6. Fill Report
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(reportItems);
            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, dataSource);

            // 7. Export to PDF bytes
            return JasperExportManager.exportReportToPdf(jasperPrint);

        } catch (Exception e) {
            log.error("Failed to generate Jasper report", e);
            throw new RuntimeException("Report generation failed: " + e.getMessage(), e);
        }
    }

    private JasperReport loadCompiledReport() throws JRException {
        if (cachedReport == null) {
            synchronized (ReportService.class) {
                if (cachedReport == null) {
                    try (java.io.InputStream is = new org.springframework.core.io.ClassPathResource("reports/sales_report.jasper").getInputStream()) {
                        cachedReport = (JasperReport) net.sf.jasperreports.engine.util.JRLoader.loadObject(is);
                    } catch (Exception e) {
                        log.error("Failed to load compiled sales_report.jasper from classpath. Ensure the test suite has run to pre-compile it.", e);
                        throw new JRException("Compiled report not found", e);
                    }
                }
            }
        }
        return cachedReport;
    }
}
