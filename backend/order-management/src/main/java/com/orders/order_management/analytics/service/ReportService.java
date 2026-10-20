package com.orders.order_management.analytics.service;

import com.orders.order_management.analytics.dto.DailyOutletReportItemDto;
import com.orders.order_management.analytics.dto.MonthlyOutletReportItemDto;
import com.orders.order_management.analytics.dto.OrderReportItemDto;
import com.orders.order_management.core.security.SecurityUtils;
import com.orders.order_management.core.service.SystemConfigService;
import com.orders.order_management.transaction.entity.Order;
import com.orders.order_management.transaction.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimpleXlsxReportConfiguration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final OrderRepository orderRepository;
    private final SystemConfigService configService;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DATE_ONLY_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter MONTH_YEAR_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy").withZone(ZoneId.systemDefault());

    public String getRestaurantName() {
        try {
            String name = configService.getConfig().getRestaurantName();
            return (name != null && !name.isBlank()) ? name : "WildBar";
        } catch (Exception e) {
            return "WildBar";
        }
    }

    public String resolveEffectiveOutletId(String requestedOutletId) {
        if (SecurityUtils.isCurrentUserAdmin()) {
            if (requestedOutletId != null && !requestedOutletId.isBlank()
                    && !"all".equalsIgnoreCase(requestedOutletId) && !"consolidated".equalsIgnoreCase(requestedOutletId)
                    && !"null".equalsIgnoreCase(requestedOutletId) && !"undefined".equalsIgnoreCase(requestedOutletId)) {
                return requestedOutletId.trim();
            }
            return null; // Admin consolidated
        }
        return SecurityUtils.getCurrentOutletId();
    }

    // ========================================================
    // 1. ORDER-BY-ORDER DETAILED SALES REPORT
    // ========================================================

    public JasperPrint prepareSalesReportPrint(Instant startDate, Instant endDate, String requestedOutletId) {
        try {
            String effectiveOutletId = resolveEffectiveOutletId(requestedOutletId);
            List<Order> orders = orderRepository.findOrdersBetween(startDate, endDate, effectiveOutletId);
            orders.sort(Comparator.comparing(Order::getCreatedAt));

            List<OrderReportItemDto> reportItems = orders.stream().map(o -> {
                String outletName = o.getOutlet() != null ? o.getOutlet().getName() : "Main Outlet";
                String outletCode = o.getOutlet() != null ? o.getOutlet().getCode() : "MAIN";
                BigDecimal total = o.getGrandTotal() != null ? o.getGrandTotal() : (o.getSubTotal() != null ? o.getSubTotal() : BigDecimal.ZERO);

                return OrderReportItemDto.builder()
                        .orderNumber(o.getOrderNumber() != null ? o.getOrderNumber() : "N/A")
                        .orderType(o.getOrderType() != null ? o.getOrderType().name() : "N/A")
                        .tableName(o.getTable() != null ? o.getTable().getTableNumber() : "Takeaway")
                        .status(o.getStatus() != null ? o.getStatus().name() : "N/A")
                        .grandTotal(total)
                        .createdAt(o.getCreatedAt() != null ? DATE_TIME_FORMATTER.format(o.getCreatedAt()) : "N/A")
                        .dateGroup(o.getCreatedAt() != null ? DATE_ONLY_FORMATTER.format(o.getCreatedAt()) : "N/A")
                        .paymentMethod(o.getPayments() != null && !o.getPayments().isEmpty() ? o.getPayments().get(0).getPaymentMethod().name() : "N/A")
                        .outletName(outletName)
                        .outletCode(outletCode)
                        .build();
            }).toList();

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("RESTAURANT_NAME", getRestaurantName());
            parameters.put("DATE_RANGE", formatDateRange(startDate, endDate));
            parameters.put("GENERATED_AT", DATE_TIME_FORMATTER.format(Instant.now()));
            parameters.put("OUTLET_FILTER", getActiveOutletFilterLabel(effectiveOutletId));

            JasperReport jasperReport = loadOrCompileReport("transactions_detail");
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(reportItems);
            return JasperFillManager.fillReport(jasperReport, parameters, dataSource);
        } catch (Exception e) {
            log.error("Failed to prepare detailed sales report", e);
            throw new RuntimeException("Detailed report generation failed: " + e.getMessage(), e);
        }
    }

    public JasperPrint prepareSalesReportPrint(Instant startDate, Instant endDate) {
        return prepareSalesReportPrint(startDate, endDate, null);
    }

    public byte[] generateSalesReportPdf(Instant startDate, Instant endDate, String requestedOutletId) {
        try {
            return JasperExportManager.exportReportToPdf(prepareSalesReportPrint(startDate, endDate, requestedOutletId));
        } catch (JRException e) {
            log.error("Failed to export detailed report to PDF", e);
            throw new RuntimeException("Detailed report PDF export failed: " + e.getMessage(), e);
        }
    }

    public byte[] generateSalesReportPdf(Instant startDate, Instant endDate) {
        return generateSalesReportPdf(startDate, endDate, null);
    }

    public byte[] generateSalesReportXlsx(Instant startDate, Instant endDate, String requestedOutletId) {
        return exportReportToXlsx(prepareSalesReportPrint(startDate, endDate, requestedOutletId));
    }

    public byte[] generateSalesReportXlsx(Instant startDate, Instant endDate) {
        return generateSalesReportXlsx(startDate, endDate, null);
    }

    // ========================================================
    // 2. CONSOLIDATED SALES SUMMARY REPORT
    // ========================================================

    public JasperPrint prepareSalesSummaryPrint(Instant startDate, Instant endDate, String requestedOutletId) {
        try {
            String effectiveOutletId = resolveEffectiveOutletId(requestedOutletId);
            List<Order> orders = orderRepository.findOrdersBetween(startDate, endDate, effectiveOutletId);
            Map<LocalDate, List<Order>> ordersByDate = new TreeMap<>();

            for (Order o : orders) {
                if (o.getCreatedAt() != null) {
                    LocalDate dateKey = o.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate();
                    ordersByDate.computeIfAbsent(dateKey, k -> new ArrayList<>()).add(o);
                }
            }

            List<Map<String, Object>> summaryData = new ArrayList<>();
            for (Map.Entry<LocalDate, List<Order>> entry : ordersByDate.entrySet()) {
                String dateStr = DATE_ONLY_FORMATTER.format(entry.getKey().atStartOfDay(ZoneId.systemDefault()));
                List<Order> dayOrders = entry.getValue().stream()
                        .filter(o -> o.getStatus() != com.orders.order_management.core.enums.OrderStatus.CANCELLED)
                        .toList();

                long count = dayOrders.size();
                BigDecimal totalRev = dayOrders.stream()
                        .map(o -> o.getGrandTotal() != null ? o.getGrandTotal() : (o.getSubTotal() != null ? o.getSubTotal() : BigDecimal.ZERO))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal avg = count > 0 ? totalRev.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

                Map<String, Object> dayMap = new HashMap<>();
                dayMap.put("date", dateStr);
                dayMap.put("orderCount", count);
                dayMap.put("totalRevenue", totalRev);
                dayMap.put("averageOrderValue", avg);
                summaryData.add(dayMap);
            }

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("RESTAURANT_NAME", getRestaurantName());
            parameters.put("DATE_RANGE", formatDateRange(startDate, endDate));
            parameters.put("GENERATED_AT", DATE_TIME_FORMATTER.format(Instant.now()));
            parameters.put("OUTLET_FILTER", getActiveOutletFilterLabel(effectiveOutletId));

            JasperReport jasperReport = loadOrCompileReport("transactions_summary");
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(summaryData);
            return JasperFillManager.fillReport(jasperReport, parameters, dataSource);
        } catch (Exception e) {
            log.error("Failed to prepare summary report", e);
            throw new RuntimeException("Summary report generation failed: " + e.getMessage(), e);
        }
    }

    public JasperPrint prepareSalesSummaryPrint(Instant startDate, Instant endDate) {
        return prepareSalesSummaryPrint(startDate, endDate, null);
    }

    public byte[] generateSalesSummaryPdf(Instant startDate, Instant endDate, String requestedOutletId) {
        try {
            return JasperExportManager.exportReportToPdf(prepareSalesSummaryPrint(startDate, endDate, requestedOutletId));
        } catch (JRException e) {
            log.error("Failed to export summary report to PDF", e);
            throw new RuntimeException("Summary report PDF export failed: " + e.getMessage(), e);
        }
    }

    public byte[] generateSalesSummaryPdf(Instant startDate, Instant endDate) {
        return generateSalesSummaryPdf(startDate, endDate, null);
    }

    public byte[] generateSalesSummaryXlsx(Instant startDate, Instant endDate, String requestedOutletId) {
        return exportReportToXlsx(prepareSalesSummaryPrint(startDate, endDate, requestedOutletId));
    }

    public byte[] generateSalesSummaryXlsx(Instant startDate, Instant endDate) {
        return generateSalesSummaryXlsx(startDate, endDate, null);
    }

    // ========================================================
    // 3. MULTI-OUTLET DAILY BREAKDOWN REPORT
    // ========================================================

    public JasperPrint prepareDailyOutletSalesReportPrint(Instant startDate, Instant endDate, String requestedOutletId) {
        try {
            String effectiveOutletId = resolveEffectiveOutletId(requestedOutletId);
            List<Order> orders = orderRepository.findOrdersBetween(startDate, endDate, effectiveOutletId);

            Map<LocalDate, Map<String, List<Order>>> grouped = new TreeMap<>();
            Map<String, String[]> outletMetadata = new HashMap<>();

            for (Order o : orders) {
                if (o.getCreatedAt() != null && o.getStatus() != com.orders.order_management.core.enums.OrderStatus.CANCELLED) {
                    LocalDate dateKey = o.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate();
                    String outletId = o.getOutlet() != null ? o.getOutlet().getId() : "default-outlet";
                    String outletName = o.getOutlet() != null ? o.getOutlet().getName() : "Main Outlet";
                    String outletCode = o.getOutlet() != null ? o.getOutlet().getCode() : "MAIN";

                    outletMetadata.putIfAbsent(outletId, new String[]{outletName, outletCode});
                    grouped.computeIfAbsent(dateKey, k -> new LinkedHashMap<>())
                           .computeIfAbsent(outletId, k -> new ArrayList<>())
                           .add(o);
                }
            }

            List<DailyOutletReportItemDto> reportItems = new ArrayList<>();
            for (Map.Entry<LocalDate, Map<String, List<Order>>> dayEntry : grouped.entrySet()) {
                LocalDate date = dayEntry.getKey();
                String dateGroupStr = DATE_ONLY_FORMATTER.format(date.atStartOfDay(ZoneId.systemDefault()));
                String dateSortKey = date.toString();

                for (Map.Entry<String, List<Order>> outEntry : dayEntry.getValue().entrySet()) {
                    String outId = outEntry.getKey();
                    List<Order> outOrders = outEntry.getValue();
                    String[] meta = outletMetadata.getOrDefault(outId, new String[]{"Main Outlet", "MAIN"});

                    long count = outOrders.size();
                    BigDecimal totalRev = outOrders.stream()
                            .map(o -> o.getGrandTotal() != null ? o.getGrandTotal() : (o.getSubTotal() != null ? o.getSubTotal() : BigDecimal.ZERO))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal avg = count > 0 ? totalRev.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

                    reportItems.add(DailyOutletReportItemDto.builder()
                            .dateGroup(dateGroupStr)
                            .dateSortKey(dateSortKey)
                            .outletId(outId)
                            .outletName(meta[0])
                            .outletCode(meta[1])
                            .orderCount(count)
                            .totalRevenue(totalRev)
                            .averageOrderValue(avg)
                            .build());
                }
            }

            reportItems.sort(Comparator.comparing(DailyOutletReportItemDto::getDateSortKey)
                    .thenComparing(DailyOutletReportItemDto::getOutletName));

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("RESTAURANT_NAME", getRestaurantName());
            parameters.put("DATE_RANGE", formatDateRange(startDate, endDate));
            parameters.put("GENERATED_AT", DATE_TIME_FORMATTER.format(Instant.now()));
            parameters.put("OUTLET_FILTER", getActiveOutletFilterLabel(effectiveOutletId));

            JasperReport jasperReport = loadOrCompileReport("multi_outlet_daily_sales");
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(reportItems);
            return JasperFillManager.fillReport(jasperReport, parameters, dataSource);
        } catch (Exception e) {
            log.error("Failed to prepare daily multi-outlet report", e);
            throw new RuntimeException("Daily multi-outlet report generation failed: " + e.getMessage(), e);
        }
    }

    public JasperPrint prepareDailyOutletSalesReportPrint(Instant startDate, Instant endDate) {
        return prepareDailyOutletSalesReportPrint(startDate, endDate, null);
    }

    public byte[] generateDailyOutletSalesReportPdf(Instant startDate, Instant endDate, String requestedOutletId) {
        try {
            return JasperExportManager.exportReportToPdf(prepareDailyOutletSalesReportPrint(startDate, endDate, requestedOutletId));
        } catch (JRException e) {
            log.error("Failed to export daily multi-outlet report to PDF", e);
            throw new RuntimeException("Daily multi-outlet report PDF export failed: " + e.getMessage(), e);
        }
    }

    public byte[] generateDailyOutletSalesReportPdf(Instant startDate, Instant endDate) {
        return generateDailyOutletSalesReportPdf(startDate, endDate, null);
    }

    public byte[] generateDailyOutletSalesReportXlsx(Instant startDate, Instant endDate, String requestedOutletId) {
        return exportReportToXlsx(prepareDailyOutletSalesReportPrint(startDate, endDate, requestedOutletId));
    }

    public byte[] generateDailyOutletSalesReportXlsx(Instant startDate, Instant endDate) {
        return generateDailyOutletSalesReportXlsx(startDate, endDate, null);
    }

    // ========================================================
    // 4. MULTI-OUTLET MONTHLY CONSOLIDATED REPORT
    // ========================================================

    public JasperPrint prepareMonthlyOutletSalesReportPrint(Instant startDate, Instant endDate, String requestedOutletId) {
        try {
            String effectiveOutletId = resolveEffectiveOutletId(requestedOutletId);
            List<Order> orders = orderRepository.findOrdersBetween(startDate, endDate, effectiveOutletId);

            Map<YearMonth, Map<String, List<Order>>> grouped = new TreeMap<>();
            Map<String, String[]> outletMetadata = new HashMap<>();

            for (Order o : orders) {
                if (o.getCreatedAt() != null && o.getStatus() != com.orders.order_management.core.enums.OrderStatus.CANCELLED) {
                    LocalDate orderDate = o.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate();
                    YearMonth ym = YearMonth.from(orderDate);
                    String outletId = o.getOutlet() != null ? o.getOutlet().getId() : "default-outlet";
                    String outletName = o.getOutlet() != null ? o.getOutlet().getName() : "Main Outlet";
                    String outletCode = o.getOutlet() != null ? o.getOutlet().getCode() : "MAIN";

                    outletMetadata.putIfAbsent(outletId, new String[]{outletName, outletCode});
                    grouped.computeIfAbsent(ym, k -> new LinkedHashMap<>())
                           .computeIfAbsent(outletId, k -> new ArrayList<>())
                           .add(o);
                }
            }

            List<MonthlyOutletReportItemDto> reportItems = new ArrayList<>();
            for (Map.Entry<YearMonth, Map<String, List<Order>>> monthEntry : grouped.entrySet()) {
                YearMonth ym = monthEntry.getKey();
                String monthGroupStr = MONTH_YEAR_FORMATTER.format(ym.atDay(1).atStartOfDay(ZoneId.systemDefault()));
                String monthSortKey = ym.toString();

                for (Map.Entry<String, List<Order>> outEntry : monthEntry.getValue().entrySet()) {
                    String outId = outEntry.getKey();
                    List<Order> outOrders = outEntry.getValue();
                    String[] meta = outletMetadata.getOrDefault(outId, new String[]{"Main Outlet", "MAIN"});

                    long count = outOrders.size();
                    BigDecimal totalRev = outOrders.stream()
                            .map(o -> o.getGrandTotal() != null ? o.getGrandTotal() : (o.getSubTotal() != null ? o.getSubTotal() : BigDecimal.ZERO))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal avg = count > 0 ? totalRev.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

                    reportItems.add(MonthlyOutletReportItemDto.builder()
                            .monthGroup(monthGroupStr)
                            .monthSortKey(monthSortKey)
                            .outletId(outId)
                            .outletName(meta[0])
                            .outletCode(meta[1])
                            .orderCount(count)
                            .totalRevenue(totalRev)
                            .averageOrderValue(avg)
                            .build());
                }
            }

            reportItems.sort(Comparator.comparing(MonthlyOutletReportItemDto::getMonthSortKey)
                    .thenComparing(MonthlyOutletReportItemDto::getOutletName));

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("RESTAURANT_NAME", getRestaurantName());
            parameters.put("DATE_RANGE", formatDateRange(startDate, endDate));
            parameters.put("GENERATED_AT", DATE_TIME_FORMATTER.format(Instant.now()));
            parameters.put("OUTLET_FILTER", getActiveOutletFilterLabel(effectiveOutletId));

            JasperReport jasperReport = loadOrCompileReport("multi_outlet_monthly_sales");
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(reportItems);
            return JasperFillManager.fillReport(jasperReport, parameters, dataSource);
        } catch (Exception e) {
            log.error("Failed to prepare monthly multi-outlet report", e);
            throw new RuntimeException("Monthly multi-outlet report generation failed: " + e.getMessage(), e);
        }
    }

    public JasperPrint prepareMonthlyOutletSalesReportPrint(Instant startDate, Instant endDate) {
        return prepareMonthlyOutletSalesReportPrint(startDate, endDate, null);
    }

    public byte[] generateMonthlyOutletSalesReportPdf(Instant startDate, Instant endDate, String requestedOutletId) {
        try {
            return JasperExportManager.exportReportToPdf(prepareMonthlyOutletSalesReportPrint(startDate, endDate, requestedOutletId));
        } catch (JRException e) {
            log.error("Failed to export monthly multi-outlet report to PDF", e);
            throw new RuntimeException("Monthly multi-outlet report PDF export failed: " + e.getMessage(), e);
        }
    }

    public byte[] generateMonthlyOutletSalesReportPdf(Instant startDate, Instant endDate) {
        return generateMonthlyOutletSalesReportPdf(startDate, endDate, null);
    }

    public byte[] generateMonthlyOutletSalesReportXlsx(Instant startDate, Instant endDate, String requestedOutletId) {
        return exportReportToXlsx(prepareMonthlyOutletSalesReportPrint(startDate, endDate, requestedOutletId));
    }

    public byte[] generateMonthlyOutletSalesReportXlsx(Instant startDate, Instant endDate) {
        return exportReportToXlsx(prepareMonthlyOutletSalesReportPrint(startDate, endDate, null));
    }

    // ========================================================
    // 5. EXCEL (XLSX) EXPORTER ENGINE
    // ========================================================

    public byte[] exportReportToXlsx(JasperPrint jasperPrint) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            JRXlsxExporter exporter = new JRXlsxExporter();
            exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
            exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(baos));

            SimpleXlsxReportConfiguration config = new SimpleXlsxReportConfiguration();
            // Crucial: Single continuous flow of rows, no artificial pagination page splitting or data truncation
            config.setOnePagePerSheet(false);
            config.setRemoveEmptySpaceBetweenRows(true);
            config.setDetectCellType(true);
            config.setWhitePageBackground(false);
            config.setIgnoreGraphics(false);
            config.setCollapseRowSpan(false);
            exporter.setConfiguration(config);

            exporter.exportReport();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to export JasperPrint to XLSX", e);
            throw new RuntimeException("XLSX report export failed: " + e.getMessage(), e);
        }
    }

    // ========================================================
    // HELPERS & REPORT COMPILATION
    // ========================================================

    private String formatDateRange(Instant start, Instant end) {
        if (start == null && end == null) return "All Time";
        if (start != null && end != null) {
            return DATE_ONLY_FORMATTER.format(start) + " to " + DATE_ONLY_FORMATTER.format(end);
        }
        return start != null ? "From " + DATE_ONLY_FORMATTER.format(start) : "Until " + DATE_ONLY_FORMATTER.format(end);
    }

    private String getActiveOutletFilterLabel(String effectiveOutletId) {
        if (effectiveOutletId != null) {
            return "Branch [ID: " + effectiveOutletId + "]";
        }
        return "Consolidated (All Outlets)";
    }

    private String getActiveOutletFilterLabel() {
        return getActiveOutletFilterLabel(SecurityUtils.getCurrentOutletId());
    }

    private JasperReport loadOrCompileReport(String reportName) throws JRException {
        try {
            ClassPathResource jasperRes = new ClassPathResource("reports/" + reportName + ".jasper");
            if (jasperRes.exists()) {
                try (java.io.InputStream is = jasperRes.getInputStream()) {
                    return (JasperReport) net.sf.jasperreports.engine.util.JRLoader.loadObject(is);
                }
            }
        } catch (Exception e) {
            log.debug("Compiled .jasper not found for {}, falling back to compile .jrxml", reportName);
        }

        try {
            ClassPathResource jrxmlRes = new ClassPathResource("reports/" + reportName + ".jrxml");
            if (jrxmlRes.exists()) {
                try (java.io.InputStream is = jrxmlRes.getInputStream()) {
                    return JasperCompileManager.compileReport(is);
                }
            }
        } catch (Exception e) {
            log.error("Failed to compile reports/{}.jrxml", reportName, e);
            throw new JRException("Failed to compile jrxml: " + e.getMessage(), e);
        }

        throw new JRException("Report " + reportName + " not found as .jasper or .jrxml");
    }
}
