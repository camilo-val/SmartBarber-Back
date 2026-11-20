package com.smartbarber.infrastructure.drivenadapter.email;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final WebClient webClient;
    private static final String EMAIL_FROM = "camilo.val1999@gmail.com";

    public Mono<Void> sendEmail(
            String recipient,
            String subject,
            String content
    ) {

        Map<String, Object> body = Map.of(
                "sender", Map.of(
                        "name", "SmartBarber",
                        "email", EMAIL_FROM
                ),
                "to", List.of(
                        Map.of("email", recipient)
                ),
                "subject", subject,
                "htmlContent", content
        );

        return webClient.post()
                .uri("/smtp/email")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .then();
    }

}

