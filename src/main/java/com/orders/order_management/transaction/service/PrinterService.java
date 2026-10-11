package com.orders.order_management.transaction.service;

import com.orders.order_management.core.entity.SystemConfig;
import com.orders.order_management.core.service.SystemConfigService;
import com.orders.order_management.transaction.entity.Order;
import com.orders.order_management.transaction.entity.OrderItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.print.*;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Copies;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrinterService {

    private final SystemConfigService configService;

    public void printReceipt(Order order) {
        SystemConfig config = configService.getConfig();
        if (!config.isThermalPrintingEnabled()) {
            return;
        }

        try {
            String receipt = generateReceiptText(order, config);
            printToThermalPrinter(receipt, config.getPrinterName());
        } catch (Exception e) {
            log.error("Failed to print thermal receipt for order: " + order.getOrderNumber(), e);
        }
    }

    private String generateReceiptText(Order order, SystemConfig config) {
        int width = config.getThermalPaperSize().equals("58mm") ? 32 : 48; // Standard chars per line

        StringBuilder sb = new StringBuilder();
        
        // Centered Header
        String restaurantTitle = config.getRestaurantName() != null && !config.getRestaurantName().isBlank()
                ? config.getRestaurantName().toUpperCase() : "WILDBAR";
        sb.append(centerText("=== " + restaurantTitle + " ===", width)).append("\n");
        if (config.getGstNumber() != null && !config.getGstNumber().isBlank()) {
            sb.append(centerText("GSTIN: " + config.getGstNumber().trim(), width)).append("\n");
        }
        sb.append(centerText("Order: " + order.getOrderNumber(), width)).append("\n");
        
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());
        sb.append(centerText(dtf.format(order.getCreatedAt()), width)).append("\n");
        
        if (order.getTable() != null) {
            sb.append(centerText("Table: " + order.getTable().getTableNumber(), width)).append("\n");
        } else {
            sb.append(centerText("Type: Take-Away", width)).append("\n");
        }
        sb.append("-".repeat(width)).append("\n");

        // Items
        for (OrderItem item : order.getItems()) {
            String qtyName = item.getQuantity() + "x " + item.getItem().getName();
            String price = item.getTotalPrice().toString();
            sb.append(formatLine(qtyName, price, width)).append("\n");
        }

        sb.append("-".repeat(width)).append("\n");
        sb.append(formatLine("SubTotal", order.getSubTotal().toString(), width)).append("\n");
        if (order.getDiscountAmount().compareTo(java.math.BigDecimal.ZERO) > 0) {
            sb.append(formatLine("Discount", "-" + order.getDiscountAmount().toString(), width)).append("\n");
        }
        sb.append(formatLine("Tax (" + config.getTaxName() + ")", order.getTaxAmount().toString(), width)).append("\n");
        sb.append("-".repeat(width)).append("\n");
        sb.append(formatLine("GRAND TOTAL", order.getGrandTotal().toString(), width)).append("\n");
        sb.append("-".repeat(width)).append("\n");
        if (config.getGstNumber() != null && !config.getGstNumber().isBlank()) {
            sb.append(centerText("GST No: " + config.getGstNumber().trim(), width)).append("\n");
        }
        sb.append(centerText("Thank you for visiting!", width)).append("\n");
        
        // Feed lines and cut
        sb.append("\n\n\n\n\n");
        // Esc/pos cut command
        sb.append((char)29).append((char)86).append((char)66).append((char)0);
        
        return sb.toString();
    }

    private void printToThermalPrinter(String text, String printerName) throws PrintException {
        PrintService printService = findPrintService(printerName);
        if (printService == null) {
            log.warn("Printer not found! Using default printer if available.");
            printService = PrintServiceLookup.lookupDefaultPrintService();
        }

        if (printService == null) {
            throw new PrintException("No default printer found.");
        }

        InputStream is = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
        DocFlavor flavor = DocFlavor.INPUT_STREAM.AUTOSENSE;
        Doc doc = new SimpleDoc(is, flavor, null);

        PrintRequestAttributeSet pras = new HashPrintRequestAttributeSet();
        pras.add(new Copies(1));

        DocPrintJob job = printService.createPrintJob();
        job.print(doc, pras);
    }

    private PrintService findPrintService(String printerName) {
        if (printerName == null || printerName.trim().isEmpty()) {
            return null;
        }
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        for (PrintService service : services) {
            if (service.getName().equalsIgnoreCase(printerName.trim())) {
                return service;
            }
        }
        return null;
    }

    private String centerText(String text, int width) {
        if (text.length() >= width) return text.substring(0, width);
        int padSize = width - text.length();
        int padStart = text.length() + padSize / 2;
        return String.format("%" + padStart + "s", text);
    }

    private String formatLine(String left, String right, int width) {
        if (left.length() + right.length() >= width - 1) {
            left = left.substring(0, width - right.length() - 2) + "..";
        }
        int spaces = width - left.length() - right.length();
        return left + " ".repeat(Math.max(0, spaces)) + right;
    }
}
