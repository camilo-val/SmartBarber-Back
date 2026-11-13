package com.smartbarber.application.usecase.subscriptionbarber;

import com.smartbarber.domain.model.employee.Employee;
import com.smartbarber.domain.model.subscriptionbarber.SubscriptionBarbershop;
import com.smartbarber.domain.port.EmployeePort;
import com.smartbarber.domain.port.subscriptionbarbershop.SubscriptionBarbershopRepositoryPort;
import com.smartbarber.infrastructure.drivenadapter.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class SendSubscriptionExpirationReminderUC {

    private final SubscriptionBarbershopRepositoryPort subscriptionPort;
    private final EmployeePort employeePort;
    private final EmailService emailService;

    private final Set<UUID> sentReminders = ConcurrentHashMap.newKeySet();

    private static final ZoneId ZONE_ID =
            ZoneId.of("America/Bogota");

    private static final String SUBJECT =
            "Aviso: tu suscripción de SmartBarber está próxima a vencer";

    public Mono<Void> execute() {

        LocalDate expirationDate = LocalDate.now(ZONE_ID).plusDays(10);

        Instant initialDate = expirationDate
                .atStartOfDay(ZONE_ID)
                .toInstant();

        Instant finalDate = expirationDate
                .plusDays(1)
                .atStartOfDay(ZONE_ID)
                .toInstant();

        return subscriptionPort.findActiveSubscriptionsExpiringBetween(
                        initialDate,
                        finalDate
                )
                .flatMap(this::sendReminder, 5)
                .then();
    }

    private Mono<Void> sendReminder(SubscriptionBarbershop subscription) {

        UUID subscriptionId = subscription.getId();

        // Evitar enviar nuevamente el mismo recordatorio.
        if (!sentReminders.add(subscriptionId)) {
            return Mono.empty();
        }

        return employeePort
                .findAdministratorByBarbershopId(subscription.getBarberId())

                .flatMap(employee -> {

                    if (employee.getEmail() == null ||
                            employee.getEmail().isBlank()) {

                        log.warn(
                                "Administrator has no email for barbershop {}",
                                subscription.getBarberId()
                        );

                        return Mono.just(false);
                    }

                    String content = buildEmailContent(subscription);

                    return emailService.sendEmail(
                            employee.getEmail(),
                            SUBJECT,
                            content
                    ).thenReturn(true);
                })

                .defaultIfEmpty(false)

                .doOnNext(sent -> {

                    if (sent) {
                        log.info(
                                "Expiration reminder sent for subscription {}",
                                subscriptionId
                        );
                    } else {
                        // Permitir intentar nuevamente en otra ejecución.
                        sentReminders.remove(subscriptionId);
                    }
                })

                .doOnError(error -> {
                    sentReminders.remove(subscriptionId);

                    log.error(
                            "Error sending expiration reminder for subscription {}",
                            subscriptionId,
                            error
                    );
                })

                .then();
    }

    private String buildEmailContent(SubscriptionBarbershop subscription) {

        String formattedDate = DateTimeFormatter
                .ofPattern("dd 'de' MMMM 'de' yyyy", new Locale("es", "CO"))
                .format(subscription.getExpirationDate().atZone(ZONE_ID));

        return """
                Hola,

                Te informamos que la suscripción de tu barbería en SmartBarber
                está próxima a vencer.

                Fecha de vencimiento: %s

                Faltan 10 días para la fecha de expiración de tu suscripción.

                Recuerda que, al llegar la fecha de vencimiento, el acceso
                a las funcionalidades de SmartBarber podría verse afectado.

                Gracias por utilizar SmartBarber.

                Equipo SmartBarber.
                """.formatted(formattedDate);
    }
}
