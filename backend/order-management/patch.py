import sys

with open("src/main/java/com/orders/order_management/transaction/service/TransactionService.java", "r", encoding="utf-8") as f:
    c = f.read()

c = c.replace(".kitchenStatus(OrderStatus.CONFIRMED.name())", ".kitchenStatus(determineInitialKitchenStatus())")
c = c.replace("order.setKitchenStatus(OrderStatus.CONFIRMED.name());", "order.setKitchenStatus(determineInitialKitchenStatus());")
c = c.replace("order.setKitchenStatus(order.getStatus() != null && order.getStatus() != OrderStatus.SOFT_ORDER ? order.getStatus().name() : OrderStatus.CONFIRMED.name());", "order.setKitchenStatus(order.getStatus() != null && order.getStatus() != OrderStatus.SOFT_ORDER ? order.getStatus().name() : determineInitialKitchenStatus());")

c = c.replace("BigDecimal effectiveTaxRate = taxRate != null ? taxRate : new BigDecimal(\"0.05\");", "BigDecimal effectiveTaxRate = configService.getConfig().getTaxRate();")

c = c.replace("\"taxRate\", taxRate != null ? taxRate : new BigDecimal(\"0.05\"),", "\"taxRate\", configService.getConfig().getTaxRate(),\n                \"defaultDiscountPercentage\", configService.getConfig().getDefaultDiscountPercentage(),\n                \"kitchenWorkflowEnabled\", configService.getConfig().isKitchenWorkflowEnabled(),")
c = c.replace("\"taxName\", taxName != null ? taxName : \"GST\",", "\"taxName\", configService.getConfig().getTaxName(),")

with open("src/main/java/com/orders/order_management/transaction/service/TransactionService.java", "w", encoding="utf-8") as f:
    f.write(c)
