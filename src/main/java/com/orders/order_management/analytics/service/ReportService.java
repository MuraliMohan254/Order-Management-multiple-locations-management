package com.orders.order_management.analytics.service;

import com.orders.order_management.analytics.dto.OrderReportItemDto;
import com.orders.order_management.core.enums.OrderType;
import com.orders.order_management.transaction.entity.Order;
import com.orders.order_management.transaction.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.TreeMap;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final OrderRepository orderRepository;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm").withZone(ZoneId.systemDefault());

    public byte[] generateSalesReportPdf(Instant startDate, Instant endDate) {
        try {
            List<Order> orders = orderRepository.findOrdersBetween(startDate, endDate);
            
            // Sort by createdAt ascending for grouping
            orders.sort((o1, o2) -> o1.getCreatedAt().compareTo(o2.getCreatedAt()));
            
            DateTimeFormatter DATE_ONLY = DateTimeFormatter.ofPattern("MMM dd, yyyy").withZone(ZoneId.systemDefault());

            List<OrderReportItemDto> reportItems = orders.stream().map(o -> OrderReportItemDto.builder()
                    .orderNumber(o.getOrderNumber() != null ? o.getOrderNumber() : "N/A")
                    .orderType(o.getOrderType() != null ? o.getOrderType().name() : "N/A")
                    .tableName(o.getTable() != null ? o.getTable().getTableNumber() : "Takeaway")
                    .status(o.getStatus() != null ? o.getStatus().name() : "N/A")
                    .grandTotal(o.getGrandTotal() != null ? o.getGrandTotal() : (o.getSubTotal() != null ? o.getSubTotal() : BigDecimal.ZERO))
                    .createdAt(o.getCreatedAt() != null ? DATE_FORMATTER.format(o.getCreatedAt()) : "N/A")
                    .dateGroup(o.getCreatedAt() != null ? DATE_ONLY.format(o.getCreatedAt()) : "N/A")
                    .paymentMethod(o.getPayments() != null && !o.getPayments().isEmpty() ? o.getPayments().get(0).getPaymentMethod().name() : "N/A")
                    .build()).toList();

            Map<String, Object> parameters = new HashMap<>();
            JasperReport jasperReport = loadCompiledReport("transactions_detail");
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(reportItems);
            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, dataSource);

            return JasperExportManager.exportReportToPdf(jasperPrint);

        } catch (Exception e) {
            log.error("Failed to generate Jasper report", e);
            throw new RuntimeException("Report generation failed: " + e.getMessage(), e);
        }
    }

    public byte[] generateSalesSummaryPdf(Instant startDate, Instant endDate) {
        try {
            List<Order> orders = orderRepository.findOrdersBetween(startDate, endDate);
            
            // Group orders by Date string
            DateTimeFormatter DATE_ONLY = DateTimeFormatter.ofPattern("MMM dd, yyyy").withZone(ZoneId.systemDefault());
            Map<LocalDate, List<Order>> ordersByDate = new TreeMap<>();
            
            for (Order o : orders) {
                if (o.getCreatedAt() != null) {
                    LocalDate dateKey = o.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate();
                    ordersByDate.computeIfAbsent(dateKey, k -> new ArrayList<>()).add(o);
                }
            }

            List<Map<String, Object>> summaryData = new ArrayList<>();
            for (Map.Entry<LocalDate, List<Order>> entry : ordersByDate.entrySet()) {
                String dateStr = DATE_ONLY.format(entry.getKey().atStartOfDay(ZoneId.systemDefault()));
                List<Order> dayOrders = entry.getValue();
                
                long count = dayOrders.size();
                BigDecimal totalRev = dayOrders.stream()
                        .map(o -> o.getGrandTotal() != null ? o.getGrandTotal() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                
                BigDecimal avg = count > 0 ? totalRev.divide(new BigDecimal(count), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

                Map<String, Object> dayMap = new HashMap<>();
                dayMap.put("date", dateStr);
                dayMap.put("orderCount", count);
                dayMap.put("totalRevenue", totalRev);
                dayMap.put("averageOrderValue", avg);
                summaryData.add(dayMap);
            }

            // Sort by date (optional, but grouping above destroys order; if sorting is needed, could parse back or just leave it for now. Let's do a simple sort by just using a TreeMap above. Wait, string sort of "MMM dd, yyyy" won't work well. Let's use TreeMap with LocalDate as key instead.)
            
            Map<String, Object> parameters = new HashMap<>();
            JasperReport jasperReport = loadCompiledReport("transactions_summary");
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(summaryData);
            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, dataSource);
            return JasperExportManager.exportReportToPdf(jasperPrint);
        } catch (Exception e) {
            log.error("Failed to generate summary report", e);
            throw new RuntimeException("Report generation failed: " + e.getMessage(), e);
        }
    }

    private JasperReport loadCompiledReport(String reportName) throws JRException {
        try (java.io.InputStream is = new org.springframework.core.io.ClassPathResource("reports/" + reportName + ".jasper").getInputStream()) {
            return (JasperReport) net.sf.jasperreports.engine.util.JRLoader.loadObject(is);
        } catch (Exception e) {
            log.error("Failed to load compiled " + reportName + ".jasper from classpath. Ensure the test suite has run to pre-compile it.", e);
            throw new JRException("Compiled report not found", e);
        }
    }
}
