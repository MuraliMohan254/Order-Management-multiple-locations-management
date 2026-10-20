package com.orders.order_management;

import com.orders.order_management.analytics.dto.OrderReportItemDto;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.design.*;
import net.sf.jasperreports.engine.type.HorizontalTextAlignEnum;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReportServiceTest {

    @Test
    void compileJasperReportTemplate() throws Exception {
        JasperDesign design = new JasperDesign();
        design.setName("SalesReport");
        design.setPageWidth(595);
        design.setPageHeight(842);
        design.setColumnWidth(555);
        design.setLeftMargin(20);
        design.setRightMargin(20);
        design.setTopMargin(20);
        design.setBottomMargin(20);

        // Parameters
        addParameter(design, "reportTitle", String.class);
        addParameter(design, "dateRangeText", String.class);
        addParameter(design, "generatedAt", String.class);
        addParameter(design, "totalRevenue", BigDecimal.class);
        addParameter(design, "totalOrders", Long.class);
        addParameter(design, "dineInCount", Long.class);
        addParameter(design, "takeAwayCount", Long.class);
        
        // New high-level totals parameters
        addParameter(design, "todayTotal", BigDecimal.class);
        addParameter(design, "monthTotal", BigDecimal.class);

        // Fields
        addField(design, "orderNumber", String.class);
        addField(design, "orderType", String.class);
        addField(design, "tableName", String.class);
        addField(design, "status", String.class);
        addField(design, "createdAtText", String.class);
        addField(design, "grandTotal", BigDecimal.class);

        // --- Title Band ---
        JRDesignBand titleBand = new JRDesignBand();
        titleBand.setHeight(130);

        JRDesignStaticText titleText = new JRDesignStaticText();
        titleText.setX(0); titleText.setY(10); titleText.setWidth(555); titleText.setHeight(30);
        titleText.setText("WildBar Restaurant - Sales Report");
        titleText.setFontSize(18f);
        titleBand.addElement(titleText);
        
        // Subtitle
        JRDesignTextField tfSubtitle = new JRDesignTextField();
        tfSubtitle.setX(0); tfSubtitle.setY(40); tfSubtitle.setWidth(555); tfSubtitle.setHeight(20);
        tfSubtitle.setExpression(new JRDesignExpression("$P{reportTitle} + \" | \" + $P{dateRangeText}"));
        titleBand.addElement(tfSubtitle);

        // High Level Summaries (Today, Month)
        JRDesignTextField tfToday = new JRDesignTextField();
        tfToday.setX(0); tfToday.setY(70); tfToday.setWidth(270); tfToday.setHeight(20);
        tfToday.setExpression(new JRDesignExpression("\"Today's Revenue: $\" + $P{todayTotal}"));
        tfToday.setFontSize(12f);
        titleBand.addElement(tfToday);

        JRDesignTextField tfMonth = new JRDesignTextField();
        tfMonth.setX(280); tfMonth.setY(70); tfMonth.setWidth(275); tfMonth.setHeight(20);
        tfMonth.setExpression(new JRDesignExpression("\"This Month's Revenue: $\" + $P{monthTotal}"));
        tfMonth.setFontSize(12f);
        tfMonth.setHorizontalTextAlign(HorizontalTextAlignEnum.RIGHT);
        titleBand.addElement(tfMonth);
        
        // Orders summary
        JRDesignTextField tfOrders = new JRDesignTextField();
        tfOrders.setX(0); tfOrders.setY(100); tfOrders.setWidth(555); tfOrders.setHeight(20);
        tfOrders.setExpression(new JRDesignExpression("\"Total Orders: \" + $P{totalOrders} + \" (Dine-In: \" + $P{dineInCount} + \", Takeaway: \" + $P{takeAwayCount} + \")\""));
        titleBand.addElement(tfOrders);

        design.setTitle(titleBand);

        // --- Column Header Band ---
        JRDesignBand headerBand = new JRDesignBand();
        headerBand.setHeight(20);
        headerBand.addElement(createStaticText("Order #", 0, 0, 80, 20, true));
        headerBand.addElement(createStaticText("Type", 80, 0, 80, 20, true));
        headerBand.addElement(createStaticText("Table", 160, 0, 80, 20, true));
        headerBand.addElement(createStaticText("Status", 240, 0, 80, 20, true));
        headerBand.addElement(createStaticText("Time", 320, 0, 135, 20, true));
        headerBand.addElement(createStaticText("Amount", 455, 0, 100, 20, true));
        design.setColumnHeader(headerBand);

        // --- Detail Band ---
        JRDesignBand detailBand = new JRDesignBand();
        detailBand.setHeight(20);
        detailBand.addElement(createTextField("$F{orderNumber}", 0, 0, 80, 20));
        detailBand.addElement(createTextField("$F{orderType}", 80, 0, 80, 20));
        detailBand.addElement(createTextField("$F{tableName}", 160, 0, 80, 20));
        detailBand.addElement(createTextField("$F{status}", 240, 0, 80, 20));
        detailBand.addElement(createTextField("$F{createdAtText}", 320, 0, 135, 20));
        detailBand.addElement(createTextField("\"$\" + $F{grandTotal}", 455, 0, 100, 20));
        ((JRDesignSection) design.getDetailSection()).addBand(detailBand);

        // --- Summary Band ---
        JRDesignBand summaryBand = new JRDesignBand();
        summaryBand.setHeight(30);
        JRDesignTextField tfTotal = new JRDesignTextField();
        tfTotal.setX(300); tfTotal.setY(5); tfTotal.setWidth(255); tfTotal.setHeight(20);
        tfTotal.setExpression(new JRDesignExpression("\"Period Total: $\" + $P{totalRevenue}"));
        tfTotal.setFontSize(14f);
        tfTotal.setHorizontalTextAlign(HorizontalTextAlignEnum.RIGHT);
        summaryBand.addElement(tfTotal);
        design.setSummary(summaryBand);

        // Compile and save
        File reportsDir = new File("src/main/resources/reports");
        if (!reportsDir.exists()) reportsDir.mkdirs();
        
        String jasperPath = "src/main/resources/reports/sales_report.jasper";
        JasperCompileManager.compileReportToFile(design, jasperPath);

        File jasperFile = new File(jasperPath);
        assertTrue(jasperFile.exists(), "Compiled .jasper file should have been created");

        // Verify it can be loaded and filled
        JasperReport jasperReport = (JasperReport) net.sf.jasperreports.engine.util.JRLoader.loadObject(jasperFile);
        assertNotNull(jasperReport);

        Map<String, Object> params = new HashMap<>();
        params.put("reportTitle", "Sales & Orders Performance Report");
        params.put("dateRangeText", "Today");
        params.put("generatedAt", "2023-10-27 10:00:00");
        params.put("totalRevenue", new BigDecimal("250.00"));
        params.put("totalOrders", 1L);
        params.put("dineInCount", 1L);
        params.put("takeAwayCount", 0L);
        params.put("todayTotal", new BigDecimal("500.00"));
        params.put("monthTotal", new BigDecimal("15000.00"));

        List<OrderReportItemDto> items = List.of(
                OrderReportItemDto.builder()
                        .orderNumber("WB-101")
                        .orderType("DINE_IN")
                        .tableName("T1")
                        .status("COMPLETED")
                        .createdAtText("10:00 AM")
                        .grandTotal(new BigDecimal("250.00"))
                        .build()
        );

        JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, params, new JRBeanCollectionDataSource(items));
        assertNotNull(jasperPrint);

        byte[] pdfBytes = JasperExportManager.exportReportToPdf(jasperPrint);
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
    }

    private void addParameter(JasperDesign design, String name, Class<?> clazz) throws JRException {
        JRDesignParameter param = new JRDesignParameter();
        param.setName(name);
        param.setValueClass(clazz);
        design.addParameter(param);
    }

    private void addField(JasperDesign design, String name, Class<?> clazz) throws JRException {
        JRDesignField field = new JRDesignField();
        field.setName(name);
        field.setValueClass(clazz);
        design.addField(field);
    }

    private JRDesignStaticText createStaticText(String text, int x, int y, int width, int height, boolean bold) {
        JRDesignStaticText st = new JRDesignStaticText();
        st.setX(x); st.setY(y); st.setWidth(width); st.setHeight(height);
        st.setText(text);
        if (bold) st.setBold(true);
        return st;
    }

    private JRDesignTextField createTextField(String expression, int x, int y, int width, int height) {
        JRDesignTextField tf = new JRDesignTextField();
        tf.setX(x); tf.setY(y); tf.setWidth(width); tf.setHeight(height);
        tf.setExpression(new JRDesignExpression(expression));
        return tf;
    }
}
