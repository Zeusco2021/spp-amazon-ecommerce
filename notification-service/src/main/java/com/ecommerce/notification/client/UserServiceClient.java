package com.ecommerce.notification.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * HTTP client for retrieving user information from the User Service.
 * Used to look up user email addresses for sending notifications.
 */
@Component
public class UserServiceClient {

    private static final Logger logger = LoggerFactory.getLogger(UserServiceClient.class);

    private final RestTemplate restTemplate;
    private final String userServiceUrl;

    public UserServiceClient(RestTemplate restTemplate,
                             @Value("${services.user-service.url:http://user-service:8081}") String userServiceUrl) {
        this.restTemplate = restTemplate;
        this.userServiceUrl = userServiceUrl;
    }

    /**
     * Retrieves the email address for a given user ID.
     *
     * @param userId the user ID
     * @return the user's email, or null if not found
     */
    @SuppressWarnings("unchecked")
    public String getUserEmail(Long userId) {
        try {
            String url = userServiceUrl + "/api/users/" + userId;
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response != null && response.containsKey("email")) {
                return (String) response.get("email");
            }
            logger.warn("No email found in user response for userId={}", userId);
            return null;
        } catch (Exception e) {
            logger.error("Failed to retrieve email for userId={}: {}", userId, e.getMessage());
            return null;
        }
    }
}
