package com.smartbarber.infrastructure.drivenadapter.scheduler;

import com.smartbarber.application.usecase.subscriptionbarber.SendSubscriptionExpirationReminderUC;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionExpirationScheduler {

    private final SendSubscriptionExpirationReminderUC reminderUC;

    @Scheduled(
            cron = "0 0 8 * * *",
            zone = "America/Bogota"
    )
    public void sendExpirationReminders() {

        log.info("Starting subscription expiration reminder process");

        reminderUC.execute()
                .doOnSuccess(unused ->
                        log.info("Subscription reminder process completed")
                )
                .doOnError(error ->
                        log.error(
                                "Error executing subscription reminder process",
                                error
                        )
                )
                .subscribe(
                        unused -> {},
                        error -> log.error(
                                "Unhandled error in subscription scheduler",
                                error
                        )
                );
    }
}
