package com.orders.order_management;

import com.orders.order_management.core.config.DatabaseAutoCreationListener;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableJpaAuditing
@EnableCaching
@EnableScheduling
public class OrderManagementApplication {

	@PostConstruct
	public void init() {
		// Enforce UTC timezone for the entire JVM
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
	}
	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(OrderManagementApplication.class);
		app.addListeners(new DatabaseAutoCreationListener());
		app.run(args);
	}

}
