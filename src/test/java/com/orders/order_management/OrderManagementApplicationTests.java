package com.orders.order_management;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.orders.order_management.transaction.controller.TransactionController;

@SpringBootTest
class OrderManagementApplicationTests {

    @Autowired
    private TransactionController transactionController;

	@Test
	void contextLoads() {
        // Test search API
        transactionController.getPagedOrders(null, null, null, 0, 20, "createdAt", "DESC");
	}
}
