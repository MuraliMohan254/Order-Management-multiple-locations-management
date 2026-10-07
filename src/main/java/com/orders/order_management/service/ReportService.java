package com.orders.order_management.service;

import com.orders.order_management.dto.OrderReportItemDto;
import com.orders.order_management.entity.Order;
import com.orders.order_management.enums.OrderType;
import com.orders.order_management.repos.OrderRepository;
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

            // 4. Get or Compile Report Design
            JasperReport jasperReport = getOrCompileReport();

            // 5. Fill Report
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(reportItems);
            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, dataSource);

            // 6. Export to PDF bytes
            return JasperExportManager.exportReportToPdf(jasperPrint);

        } catch (Exception e) {
            log.error("Failed to generate JasperReports PDF", e);
            throw new RuntimeException("Error generating sales report PDF: " + e.getMessage(), e);
        }
    }

    private synchronized JasperReport getOrCompileReport() throws JRException {
        if (cachedReport == null) {
            JasperDesign design = buildReportDesign();
            cachedReport = JasperCompileManager.compileReport(design);
        }
        return cachedReport;
    }

    private JasperDesign buildReportDesign() throws JRException {
        JasperDesign design = new JasperDesign();
        design.setName("WildBarSalesReport");
        design.setPageWidth(595);
        design.setPageHeight(842);
        design.setColumnWidth(555);
        design.setLeftMargin(20);
        design.setRightMargin(20);
        design.setTopMargin(20);
        design.setBottomMargin(20);

        // Parameters
        addParam(design, "reportTitle", String.class);
        addParam(design, "dateRangeText", String.class);
        addParam(design, "generatedAt", String.class);
        addParam(design, "totalRevenueText", String.class);
        addParam(design, "totalOrdersText", String.class);
        addParam(design, "dineInText", String.class);
        addParam(design, "takeAwayText", String.class);

        // Fields from OrderReportItemDto
        addField(design, "orderNumber", String.class);
        addField(design, "orderType", String.class);
        addField(design, "tableName", String.class);
        addField(design, "status", String.class);
        addField(design, "grandTotal", BigDecimal.class);
        addField(design, "createdAtText", String.class);

        // 1. TITLE BAND (Dark Header Card)
        JRDesignBand titleBand = new JRDesignBand();
        titleBand.setHeight(100);

        JRDesignRectangle bgRect = new JRDesignRectangle();
        bgRect.setX(0); bgRect.setY(0); bgRect.setWidth(555); bgRect.setHeight(92);
        bgRect.setBackcolor(new Color(15, 23, 42)); // Slate 900
        bgRect.getLinePen().setLineWidth(0f);
        titleBand.addElement(bgRect);

        JRDesignStaticText brandText = new JRDesignStaticText();
        brandText.setX(16); brandText.setY(12); brandText.setWidth(280); brandText.setHeight(26);
        brandText.setForecolor(Color.WHITE);
        brandText.setFontSize(18f);
        brandText.setBold(true);
        brandText.setText("WildBar Restaurant");
        titleBand.addElement(brandText);

        JRDesignTextField titleTf = new JRDesignTextField();
        titleTf.setX(16); titleTf.setY(40); titleTf.setWidth(300); titleTf.setHeight(18);
        titleTf.setForecolor(new Color(148, 163, 184)); // Slate 400
        titleTf.setFontSize(10f);
        titleTf.setBold(true);
        titleTf.setExpression(createExpr("$P{reportTitle}"));
        titleBand.addElement(titleTf);

        JRDesignTextField rangeTf = new JRDesignTextField();
        rangeTf.setX(16); rangeTf.setY(60); rangeTf.setWidth(320); rangeTf.setHeight(16);
        rangeTf.setForecolor(new Color(100, 116, 139));
        rangeTf.setFontSize(8.5f);
        rangeTf.setExpression(createExpr("\"Period: \" + $P{dateRangeText}"));
        titleBand.addElement(rangeTf);

        JRDesignStaticText revLabel = new JRDesignStaticText();
        revLabel.setX(340); revLabel.setY(16); revLabel.setWidth(200); revLabel.setHeight(16);
        revLabel.setForecolor(new Color(148, 163, 184));
        revLabel.setFontSize(9f);
        revLabel.setHorizontalTextAlign(HorizontalTextAlignEnum.RIGHT);
        revLabel.setText("Total Period Revenue");
        titleBand.addElement(revLabel);

        JRDesignTextField revTf = new JRDesignTextField();
        revTf.setX(340); revTf.setY(34); revTf.setWidth(200); revTf.setHeight(28);
        revTf.setForecolor(new Color(56, 189, 248)); // Sky 400
        revTf.setFontSize(16f);
        revTf.setBold(true);
        revTf.setHorizontalTextAlign(HorizontalTextAlignEnum.RIGHT);
        revTf.setExpression(createExpr("$P{totalRevenueText}"));
        titleBand.addElement(revTf);

        JRDesignTextField genTf = new JRDesignTextField();
        genTf.setX(340); genTf.setY(64); genTf.setWidth(200); genTf.setHeight(14);
        genTf.setForecolor(new Color(100, 116, 139));
        genTf.setFontSize(8f);
        genTf.setHorizontalTextAlign(HorizontalTextAlignEnum.RIGHT);
        genTf.setExpression(createExpr("\"Generated: \" + $P{generatedAt}"));
        titleBand.addElement(genTf);

        design.setTitle(titleBand);

        // 2. PAGE HEADER (KPI summary bar)
        JRDesignBand pageHeader = new JRDesignBand();
        pageHeader.setHeight(36);

        JRDesignRectangle kpiBar = new JRDesignRectangle();
        kpiBar.setX(0); kpiBar.setY(4); kpiBar.setWidth(555); kpiBar.setHeight(26);
        kpiBar.setBackcolor(new Color(248, 250, 252));
        kpiBar.getLinePen().setLineWidth(1f);
        kpiBar.getLinePen().setLineColor(new Color(226, 232, 240));
        pageHeader.addElement(kpiBar);

        JRDesignTextField kpiOrders = new JRDesignTextField();
        kpiOrders.setX(16); kpiOrders.setY(9); kpiOrders.setWidth(160); kpiOrders.setHeight(16);
        kpiOrders.setFontSize(9f);
        kpiOrders.setBold(true);
        kpiOrders.setForecolor(new Color(30, 41, 59));
        kpiOrders.setExpression(createExpr("\"Total Orders: \" + $P{totalOrdersText}"));
        pageHeader.addElement(kpiOrders);

        JRDesignTextField kpiDineIn = new JRDesignTextField();
        kpiDineIn.setX(195); kpiDineIn.setY(9); kpiDineIn.setWidth(160); kpiDineIn.setHeight(16);
        kpiDineIn.setFontSize(9f);
        kpiDineIn.setBold(true);
        kpiDineIn.setForecolor(new Color(37, 99, 235)); // Blue 600
        kpiDineIn.setExpression(createExpr("\"Dine-In: \" + $P{dineInText}"));
        pageHeader.addElement(kpiDineIn);

        JRDesignTextField kpiTakeAway = new JRDesignTextField();
        kpiTakeAway.setX(375); kpiTakeAway.setY(9); kpiTakeAway.setWidth(165); kpiTakeAway.setHeight(16);
        kpiTakeAway.setFontSize(9f);
        kpiTakeAway.setBold(true);
        kpiTakeAway.setHorizontalTextAlign(HorizontalTextAlignEnum.RIGHT);
        kpiTakeAway.setForecolor(new Color(217, 119, 6)); // Amber 600
        kpiTakeAway.setExpression(createExpr("\"Takeaway: \" + $P{takeAwayText}"));
        pageHeader.addElement(kpiTakeAway);

        design.setPageHeader(pageHeader);

        // 3. COLUMN HEADER (Table Columns)
        JRDesignBand columnHeader = new JRDesignBand();
        columnHeader.setHeight(24);

        JRDesignRectangle thBg = new JRDesignRectangle();
        thBg.setX(0); thBg.setY(2); thBg.setWidth(555); thBg.setHeight(20);
        thBg.setBackcolor(new Color(30, 41, 59)); // Slate 800
        thBg.getLinePen().setLineWidth(0f);
        columnHeader.addElement(thBg);

        addColHeader(columnHeader, 10, "ORDER #", 110, HorizontalTextAlignEnum.LEFT);
        addColHeader(columnHeader, 125, "TYPE", 75, HorizontalTextAlignEnum.LEFT);
        addColHeader(columnHeader, 205, "TABLE", 65, HorizontalTextAlignEnum.LEFT);
        addColHeader(columnHeader, 275, "STATUS", 75, HorizontalTextAlignEnum.LEFT);
        addColHeader(columnHeader, 355, "DATE & TIME", 115, HorizontalTextAlignEnum.LEFT);
        addColHeader(columnHeader, 475, "AMOUNT", 70, HorizontalTextAlignEnum.RIGHT);

        design.setColumnHeader(columnHeader);

        // 4. DETAIL BAND (Data row)
        JRDesignBand detailBand = new JRDesignBand();
        detailBand.setHeight(20);

        addCellText(detailBand, 10, "$F{orderNumber}", 110, HorizontalTextAlignEnum.LEFT, true);
        addCellText(detailBand, 125, "$F{orderType}", 75, HorizontalTextAlignEnum.LEFT, false);
        addCellText(detailBand, 205, "$F{tableName}", 65, HorizontalTextAlignEnum.LEFT, false);
        addCellText(detailBand, 275, "$F{status}", 75, HorizontalTextAlignEnum.LEFT, false);
        addCellText(detailBand, 355, "$F{createdAtText}", 115, HorizontalTextAlignEnum.LEFT, false);
        addCellText(detailBand, 475, "\"INR \" + $F{grandTotal}", 70, HorizontalTextAlignEnum.RIGHT, true);

        // Subtle row bottom divider line
        JRDesignLine rowLine = new JRDesignLine();
        rowLine.setX(0); rowLine.setY(19); rowLine.setWidth(555); rowLine.setHeight(1);
        rowLine.getLinePen().setLineWidth(0.5f);
        rowLine.getLinePen().setLineColor(new Color(241, 245, 249));
        detailBand.addElement(rowLine);

        ((JRDesignSection) design.getDetailSection()).addBand(detailBand);

        // 5. PAGE FOOTER
        JRDesignBand pageFooter = new JRDesignBand();
        pageFooter.setHeight(24);

        JRDesignStaticText footConf = new JRDesignStaticText();
        footConf.setX(10); footConf.setY(6); footConf.setWidth(300); footConf.setHeight(14);
        footConf.setFontSize(7.5f);
        footConf.setForecolor(new Color(148, 163, 184));
        footConf.setText("WildBar POS Management  |  Confidential");
        pageFooter.addElement(footConf);

        JRDesignTextField pageNumTf = new JRDesignTextField();
        pageNumTf.setX(395); pageNumTf.setY(6); pageNumTf.setWidth(150); pageNumTf.setHeight(14);
        pageNumTf.setFontSize(7.5f);
        pageNumTf.setForecolor(new Color(148, 163, 184));
        pageNumTf.setHorizontalTextAlign(HorizontalTextAlignEnum.RIGHT);
        pageNumTf.setExpression(createExpr("\"Page \" + $V{PAGE_NUMBER}"));
        pageFooter.addElement(pageNumTf);

        design.setPageFooter(pageFooter);

        return design;
    }

    private void addParam(JasperDesign design, String name, Class<?> clazz) throws JRException {
        JRDesignParameter p = new JRDesignParameter();
        p.setName(name);
        p.setValueClass(clazz);
        design.addParameter(p);
    }

    private void addField(JasperDesign design, String name, Class<?> clazz) throws JRException {
        JRDesignField f = new JRDesignField();
        f.setName(name);
        f.setValueClass(clazz);
        design.addField(f);
    }

    private void addColHeader(JRDesignBand band, int x, String text, int width, HorizontalTextAlignEnum align) {
        JRDesignStaticText st = new JRDesignStaticText();
        st.setX(x); st.setY(5); st.setWidth(width); st.setHeight(14);
        st.setForecolor(Color.WHITE);
        st.setFontSize(7.5f);
        st.setBold(true);
        st.setHorizontalTextAlign(align);
        st.setText(text);
        band.addElement(st);
    }

    private void addCellText(JRDesignBand band, int x, String exprText, int width, HorizontalTextAlignEnum align, boolean bold) {
        JRDesignTextField tf = new JRDesignTextField();
        tf.setX(x); tf.setY(2); tf.setWidth(width); tf.setHeight(15);
        tf.setFontSize(8f);
        tf.setBold(bold);
        tf.setForecolor(new Color(30, 41, 59));
        tf.setHorizontalTextAlign(align);
        tf.setVerticalTextAlign(VerticalTextAlignEnum.MIDDLE);
        tf.setExpression(createExpr(exprText));
        band.addElement(tf);
    }

    private JRDesignExpression createExpr(String text) {
        JRDesignExpression expr = new JRDesignExpression();
        expr.setText(text);
        return expr;
    }
}
