package com.orders.order_management;

import com.orders.order_management.dto.OrderReportItemDto;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.design.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReportServiceTest {

    @Test
    void testProgrammaticJasperReport() throws Exception {
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
        JRDesignParameter paramTitle = new JRDesignParameter();
        paramTitle.setName("reportTitle");
        paramTitle.setValueClass(String.class);
        design.addParameter(paramTitle);

        // Fields
        JRDesignField fOrderNum = new JRDesignField();
        fOrderNum.setName("orderNumber");
        fOrderNum.setValueClass(String.class);
        design.addField(fOrderNum);

        JRDesignField fAmount = new JRDesignField();
        fAmount.setName("grandTotal");
        fAmount.setValueClass(BigDecimal.class);
        design.addField(fAmount);

        // Title Band
        JRDesignBand titleBand = new JRDesignBand();
        titleBand.setHeight(50);
        JRDesignStaticText titleText = new JRDesignStaticText();
        titleText.setX(0);
        titleText.setY(10);
        titleText.setWidth(555);
        titleText.setHeight(30);
        titleText.setText("WildBar Restaurant Performance Report");
        titleBand.addElement(titleText);
        design.setTitle(titleBand);

        // Detail Band
        JRDesignBand detailBand = new JRDesignBand();
        detailBand.setHeight(20);
        
        JRDesignTextField tfOrder = new JRDesignTextField();
        tfOrder.setX(0);
        tfOrder.setY(0);
        tfOrder.setWidth(200);
        tfOrder.setHeight(20);
        JRDesignExpression expOrder = new JRDesignExpression();
        expOrder.setText("$F{orderNumber}");
        tfOrder.setExpression(expOrder);
        detailBand.addElement(tfOrder);

        ((JRDesignSection) design.getDetailSection()).addBand(detailBand);

        // Compile
        JasperReport jasperReport = JasperCompileManager.compileReport(design);
        assertNotNull(jasperReport);

        // Fill
        Map<String, Object> params = new HashMap<>();
        params.put("reportTitle", "Sales & Orders Performance Report");

        List<OrderReportItemDto> items = List.of(
                OrderReportItemDto.builder().orderNumber("WB-101").grandTotal(new BigDecimal("250.00")).build()
        );

        JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, params, new JRBeanCollectionDataSource(items));
        assertNotNull(jasperPrint);

        // Export to PDF
        byte[] pdfBytes = JasperExportManager.exportReportToPdf(jasperPrint);
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
    }
}
