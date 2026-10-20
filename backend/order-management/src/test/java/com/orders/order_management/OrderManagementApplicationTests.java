package com.orders.order_management;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.orders.order_management.transaction.controller.TransactionController;

import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "debug=true")
class OrderManagementApplicationTests {

    @Autowired
    private TransactionController transactionController;

    @Autowired
    private com.orders.order_management.core.controller.SystemConfigController systemConfigController;

	@Test
	void contextLoads() {
        // Test search API
        transactionController.getPagedOrders(null, null, null, null, 0, 20, "createdAt", "DESC");
	}

    @Test
    void testSystemConfigSerializationAndSave() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var response = systemConfigController.getConfig();
        String json1 = mapper.writeValueAsString(response.getBody());
        org.junit.jupiter.api.Assertions.assertNotNull(json1);

        var updateResponse = systemConfigController.updateConfig(response.getBody());
        String json2 = mapper.writeValueAsString(updateResponse.getBody());
        org.junit.jupiter.api.Assertions.assertNotNull(json2);
    }
}
