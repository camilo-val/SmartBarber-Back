package com.smartbarber.infrastructure.drivenadapter.email;

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
    public Mono<Void> sendEmail(
            String recipient,
            String subject,
            String content
    ) {
        return Mono.fromRunnable(() -> {
                    SimpleMailMessage message = new SimpleMailMessage();
                    message.setTo(recipient);
                    message.setSubject(subject);
                    message.setText(content);

                    mailSender.send(message);
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

