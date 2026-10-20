import sys

with open("src/main/java/com/orders/order_management/transaction/service/TransactionService.java", "r", encoding="utf-8") as f:
    content = f.read()

content = content.replace('"ORD-" + System.currentTimeMillis()', '"ORD-" + System.currentTimeMillis() + "-" + java.util.UUID.randomUUID().toString().substring(0,4).toUpperCase()')
content = content.replace('"TA-" + System.currentTimeMillis()', '"TA-" + System.currentTimeMillis() + "-" + java.util.UUID.randomUUID().toString().substring(0,4).toUpperCase()')
content = content.replace('"POS-" + System.currentTimeMillis()', '"POS-" + System.currentTimeMillis() + "-" + java.util.UUID.randomUUID().toString().substring(0,4).toUpperCase()')

with open("src/main/java/com/orders/order_management/transaction/service/TransactionService.java", "w", encoding="utf-8") as f:
    f.write(content)
