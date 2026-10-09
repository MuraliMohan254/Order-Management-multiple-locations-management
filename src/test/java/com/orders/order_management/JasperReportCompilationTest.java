package com.orders.order_management;

import net.sf.jasperreports.engine.JasperCompileManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;

@SpringBootTest
class JasperReportCompilationTest {

    @Test
    void compileJasperReports() throws Exception {
        String reportsDir = "src/main/resources/reports";
        File dir = new File(reportsDir);
        if (dir.exists() && dir.isDirectory()) {
            File[] jrxmlFiles = dir.listFiles((d, name) -> name.endsWith(".jrxml"));
            if (jrxmlFiles != null) {
                for (File jrxmlFile : jrxmlFiles) {
                    String compiledPath = jrxmlFile.getAbsolutePath().replace(".jrxml", ".jasper");
                    JasperCompileManager.compileReportToFile(jrxmlFile.getAbsolutePath(), compiledPath);
                    System.out.println("Compiled: " + jrxmlFile.getName() + " -> " + compiledPath);
                }
            }
        }
    }
}
