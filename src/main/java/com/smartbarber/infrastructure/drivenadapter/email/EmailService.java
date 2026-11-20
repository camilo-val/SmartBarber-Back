package com.smartbarber.infrastructure.drivenadapter.email;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final JavaMailSenderImpl mailSenderImpl;
    private static final String EMAIL_FROM = "camilo.val1999@gmail.com";

    public Mono<Void> sendEmail(
            String recipient,
            String subject,
            String content
    ) {
        return Mono.fromRunnable(() -> {
                    try {

                        SimpleMailMessage message = new SimpleMailMessage();

                        message.setFrom(EMAIL_FROM);
                        message.setTo(recipient);
                        message.setSubject(subject);
                        message.setText(content);

                        mailSender.send(message);

                    } catch (Exception e) {

                        Throwable root = e;

                        while (root.getCause() != null) {
                            root = root.getCause();
                        }

                        root.printStackTrace();

                        throw new RuntimeException(
                                "ROOT SMTP ERROR: "
                                        + root.getClass().getName()
                                        + " -> "
                                        + root.getMessage(),
                                e
                        );
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }
    public boolean testConnection() {
        try {
            mailSenderImpl.testConnection();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}

