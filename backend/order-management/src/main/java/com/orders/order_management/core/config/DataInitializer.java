package com.orders.order_management.core.config;


import com.orders.order_management.auth.dto.OnboardUserRequest;
import com.orders.order_management.auth.repository.UserRepository;
import com.orders.order_management.auth.service.OnboardUserService;
import com.orders.order_management.core.enums.Role;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initializeData(
            UserRepository userRepository,
            OnboardUserService userService
    ) {
        return args -> {
            System.out.println("Checking default user...");
            if (userRepository.count() == 0) {
                OnboardUserRequest newUser = new OnboardUserRequest();
                newUser.setUsername("admin");
                newUser.setRole(Role.valueOf("ADMIN"));
                newUser.setPassword("admin");
                userService.registerUser(newUser);
                System.out.println("Default user created.");
            }
        };
    }
}
