package com.smartbarber.application.usecase.reservation.servicereservation;

import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.reservation.ReservationMessageExceptions;
import com.smartbarber.domain.model.customer.Customer;
import com.smartbarber.domain.model.notification.Notification;
import com.smartbarber.domain.model.reservation.Reservation;
import com.smartbarber.application.usecase.queue.QueueBroadcastService;
import com.smartbarber.domain.enums.NotificationType;
import com.smartbarber.domain.enums.ReservationStatus;
import com.smartbarber.domain.enums.ReservationType;
import com.smartbarber.application.usecase.security.AuthorizationAccessRules;
import com.smartbarber.domain.port.authentication.CurrentAuthenticationPort;
import com.smartbarber.domain.model.authentication.Authentication;
import org.springframework.beans.factory.annotation.Value;
import java.time.Duration;
import java.util.function.BiFunction;
import com.smartbarber.domain.port.CustomerPort;
import com.smartbarber.domain.port.NotificationPort;
import com.smartbarber.domain.port.reservation.ReservationRepositoryPort;
import com.smartbarber.infrastructure.drivenadapter.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.time.OffsetDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Log4j2
public class ChangeReservationStatusUc {

    private final ReservationRepositoryPort reservationRepositoryPort;
    private final CustomerPort customerPort;
    private final NotificationPort notificationPort;
        private final EmailService emailService;
            private final CurrentAuthenticationPort currentAuthenticationPort;
    private final QueueBroadcastService queueBroadcastService;

    /** Anticipación mínima con que el cliente puede cancelar o reprogramar. */
    @Value("${reservation.customer-change-notice-minutes:60}")
    private long customerChangeNoticeMinutes;

    Duration customerChangeNotice() {
        return Duration.ofMinutes(customerChangeNoticeMinutes);
    }

    public Mono<Reservation> confirmReservation(UUID reservationId) {
        return changeStatus(
                reservationId,
                (user, reservation) -> reservation.confirmar(),
                "¡Tu reserva ha sido confirmada!",
                "RESERVATION_CONFIRMED",
                "¡Tenemos excelentes noticias!\n\n"
                        + "Tu reserva en SmartBarber ha sido confirmada correctamente. "
                        + "Estamos encantados de atenderte y esperamos que disfrutes "
                        + "de una excelente experiencia con nosotros.\n\n"
                        + "Te recomendamos tener presente el horario de tu cita "
                        + "para que podamos brindarte una atención puntual y de calidad.\n\n"
                        + "Si necesitas realizar algún cambio o tienes alguna inquietud, "
                        + "comunícate con nosotros con anticipación."
        );
    }

    public Mono<Reservation> cancelReservation(UUID reservationId) {
        return changeStatus(
                reservationId,
                (user, reservation) -> {
                    // El cliente debe cancelar con la anticipación mínima (RES_018).
                    if (AuthorizationAccessRules.isCustomer(user)) {
                        reservation.ensureChangeAllowed(OffsetDateTime.now(), customerChangeNotice());
                    }
                    return reservation.cancelar();
                },
                "Notificación de cancelación de reserva",
                "RESERVATION_CANCELLED",
                "Te informamos que tu reserva en SmartBarber ha sido cancelada correctamente.\n\n"
                        + "Lamentamos que no puedas asistir a tu cita y esperamos tener "
                        + "la oportunidad de atenderte próximamente.\n\n"
                        + "Si deseas disfrutar de nuestros servicios en otra ocasión, "
                        + "estaremos encantados de recibir una nueva reserva.\n\n"
                        + "Si no solicitaste esta cancelación o tienes alguna pregunta, "
                        + "por favor comunícate con nosotros."
        );
    }

        /** El barbero o el administrador finalizan la reserva atendida. */
    public Mono<Reservation> completeReservation(UUID reservationId) {
        return changeStatus(
                reservationId,
                (user, reservation) -> reservation.finalizar(OffsetDateTime.now()),
                "¡Gracias por tu visita!",
                "RESERVATION_COMPLETED",
                "Tu servicio en SmartBarber fue finalizado.\n\n"
                        + "Gracias por elegirnos. Desde tu historial de reservas puedes "
                        + "calificar la atención que recibiste: tu opinión nos ayuda a mejorar."
        );
    }

    /** El barbero o el administrador registran que el cliente no se presentó. */
    public Mono<Reservation> markNoShow(UUID reservationId) {
        return changeStatus(
                reservationId,
                (user, reservation) -> reservation.registrarInasistencia(OffsetDateTime.now()),
                null,
                null,
                null
        );
    }

    private Mono<Reservation> changeStatus(
            UUID reservationId,
            BiFunction<Authentication, Reservation, Reservation> statusTransition,
            String notificationTitle,
            String eventType,
            String message
    ) {
        return reservationRepositoryPort.findById(reservationId)
                .switchIfEmpty(Mono.error(
                        new BusinessExceptions(
                                ReservationMessageExceptions.RESERVATION_NOT_FOUND
                        )
                ))
                // Solo el cliente de la reserva, su barbero o el administrador de la
                // barbería pueden cambiarla (las rutas ya limitan qué rol hace qué).
                .flatMap(reservation -> currentAuthenticationPort.getCurrentAuthentication()
                        .switchIfEmpty(Mono.error(() -> new BusinessExceptions(
                                ReservationMessageExceptions.RESERVATION_NOT_FOUND)))
                        .map(user -> {
                            AuthorizationAccessRules.validateReservationAccess(user, reservation);
                            return statusTransition.apply(user, reservation);
                        }))
                .flatMap(reservationRepositoryPort::save)
                // Si era un turno de la cola virtual, la cola se actualiza para todos.
                .flatMap(reservation -> reservation.getReservationType() == ReservationType.VIRTUAL_QUEUE
                        && reservation.getStatus() == ReservationStatus.CANCELLED
                        ? queueBroadcastService.publish(reservation.getEmployeeId(), NotificationType.QUEUE_CUSTOMER_LEFT)
                                .onErrorResume(error -> Mono.empty())
                                .thenReturn(reservation)
                        : Mono.just(reservation))
                .flatMap(reservation -> message == null
                        ? Mono.just(reservation)
                        : createNotificationAndSendEmail(
                                reservation,
                                notificationTitle,
                                eventType,
                                message
                        ).thenReturn(reservation)
                );
    }

    /** Registra la notificación y envía el correo al cliente de la reserva. */
    Mono<Void> createNotificationAndSendEmail(
            Reservation reservation,
            String title,
            String eventType,
            String message
    ) {
        return customerPort.findById(reservation.getCustomerId())
                .switchIfEmpty(Mono.error(
                        new IllegalStateException("Customer not found")
                ))
                .flatMap(customer -> {
                    String content = buildReservationEmail(
                            customer,
                            reservation,
                            message
                    );

                    Notification notification = Notification.createNotification(
                            customer.getId(),
                            reservation.getId(),
                            title,
                            content,
                            "EMAIL",
                            eventType,
                            "PENDING",
                            0,
                            null,
                            Instant.now(),
                            null
                    );

                    return notificationPort.save(notification)
                            .flatMap(savedNotification ->
                                    sendAndUpdateNotification(
                                            savedNotification,
                                            customer.getEmail()
                                    )
                            );
                })
                .onErrorResume(error -> {
                    log.error(
                            "Could not process notification for reservation {}",
                            reservation.getId(),
                            error
                    );
                    return Mono.empty();
                });
    }

    private Mono<Void> sendAndUpdateNotification(
            Notification notification,
            String customerEmail
    ) {
        return emailService.sendEmail(
                        customerEmail,
                        notification.getTitle(),
                        notification.getContent()
                )
                .then(Mono.defer(() -> {
                    Notification sentNotification =
                            notification.updateNotification(
                                    notification.getId(),
                                    notification.getCustomerId(),
                                    notification.getReservationId(),
                                    notification.getTitle(),
                                    notification.getContent(),
                                    notification.getChannelType(),
                                    notification.getEventType(),
                                    "SENT",
                                    notification.getRetries(),
                                    null,
                                    notification.getScheduledDate(),
                                    Instant.now()
                            );

                    return notificationPort.update(
                                    notification.getId(),
                                    sentNotification
                            )
                            .then();
                }))
                .onErrorResume(error -> {
                    log.error(
                            "Email could not be sent for notification {}",
                            notification.getId(),
                            error
                    );

                    Notification failedNotification =
                            notification.updateNotification(
                                    notification.getId(),
                                    notification.getCustomerId(),
                                    notification.getReservationId(),
                                    notification.getTitle(),
                                    notification.getContent(),
                                    notification.getChannelType(),
                                    notification.getEventType(),
                                    "FAILED",
                                    notification.getRetries() + 1,
                                    error.getMessage(),
                                    notification.getScheduledDate(),
                                    null
                            );

                    return notificationPort.update(
                                    notification.getId(),
                                    failedNotification
                            )
                            .onErrorResume(updateError -> {
                                log.error(
                                        "Could not update notification {}",
                                        notification.getId(),
                                        updateError
                                );
                                return Mono.empty();
                            })
                            .then();
                });
    }


    private String buildReservationEmail(
            Customer customer,
            Reservation reservation,
            String message
    ) {
        DateTimeFormatter dateFormatter =
                DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", new Locale("es", "CO"))
                        .withZone(ZoneId.of("America/Bogota"));

        DateTimeFormatter timeFormatter =
                DateTimeFormatter.ofPattern("h:mm a", new Locale("es", "CO"))
                        .withZone(ZoneId.of("America/Bogota"));

        String status = switch (String.valueOf(reservation.getStatus())) {
            case "PENDING" -> "Pendiente de confirmación";
            case "CONFIRMED" -> "Confirmada";
            case "CANCELLED" -> "Cancelada";
            case "IN_PROGRESS" -> "En progreso";
            case "COMPLETED" -> "Completada";
            case "NO_SHOW" -> "Cliente no asistió";
            default -> "Estado actualizado";
        };
        String notes = reservation.getNotes() != null
                && !reservation.getNotes().isBlank()
                ? reservation.getNotes()
                : "Sin observaciones adicionales.";

        return "Hola, " + customer.getName() + ":\n\n"
                + message + "\n\n"
                + "DETALLES DE TU RESERVA\n\n"
                + "Fecha: " + dateFormatter.format(reservation.getStartTime()) + "\n"
                + "Hora de inicio: " + timeFormatter.format(reservation.getStartTime()) + "\n"
                + "Hora estimada de finalización: "
                + timeFormatter.format(reservation.getEndTime()) + "\n"
                + "Estado: " + status + ".\n\n"
                + "Observaciones: " + notes + "\n\n"
                + "¡Gracias por elegir SmartBarber! Será un placer atenderte.\n\n"
                + "Saludos cordiales,\n"
                + "El equipo de SmartBarber";
    }

}

