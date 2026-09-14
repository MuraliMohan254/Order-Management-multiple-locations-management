package com.orders.order_management.service.user;

import com.orders.order_management.dto.user.SubscriptionCheckResult;
import com.orders.order_management.dto.user.SubscriptionResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import lombok.Data;

@Service
public class SubscriptionService {

    @Value("${app.subscription.url}")
    private String subscriptionUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public SubscriptionCheckResult checkSubscription() {
        try {
            String fullUrl = subscriptionUrl;

            // Call AWS
            ResponseEntity<SubscriptionResponse> response = restTemplate.getForEntity(fullUrl, SubscriptionResponse.class);

            if (response.getBody() != null) {
                return new SubscriptionCheckResult(
                        response.getBody().getSubscription(),
                        response.getBody().getExpiry_date(),
                        response.getBody().getNotify_date()
                );
            }
        } catch (Exception e) {
            System.err.println("License Check Failed: " + e.getMessage());
        }
        // Default fallback if check fails
        return new SubscriptionCheckResult("INACTIVE", "N/A", "N/A");
    }

}