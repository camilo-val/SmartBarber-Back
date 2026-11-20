package com.smartbarber.infrastructure.drivenadapter.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class BrevoConfig {

    @Bean
    public WebClient brevoWebClient(
            @Value("${brevo.api-key}") String apiKey
    ) {
        return WebClient.builder()
                .baseUrl("https://api.brevo.com/v3")
                .defaultHeader("api-key", apiKey)
                .defaultHeader("accept", "application/json")
                .defaultHeader("content-type", "application/json")
                .build();
    }
}