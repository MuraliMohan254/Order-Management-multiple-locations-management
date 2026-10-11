package com.orders.order_management;

import net.sf.jasperreports.engine.JasperCompileManager;
import org.junit.jupiter.api.Test;

public class TestJasper {
    @Test
    public void runtest() {
        try {
            JasperCompileManager.compileReportToFile("src/main/resources/reports/transactions_detail.jrxml", "src/main/resources/reports/transactions_detail.jasper");
            JasperCompileManager.compileReportToFile("src/main/resources/reports/transactions_summary.jrxml", "src/main/resources/reports/transactions_summary.jasper");
            JasperCompileManager.compileReportToFile("src/main/resources/reports/multi_outlet_daily_sales.jrxml", "src/main/resources/reports/multi_outlet_daily_sales.jasper");
            JasperCompileManager.compileReportToFile("src/main/resources/reports/multi_outlet_monthly_sales.jrxml", "src/main/resources/reports/multi_outlet_monthly_sales.jasper");
            System.out.println("All reports compiled successfully!");
        } catch (Exception e) {
            System.err.println("JASPER ERROR: " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("CAUSE 1: " + e.getCause().getMessage());
                if (e.getCause().getCause() != null) {
                    System.err.println("CAUSE 2: " + e.getCause().getCause().getMessage());
                }
            }
            throw new RuntimeException(e);
        }
    }
}
