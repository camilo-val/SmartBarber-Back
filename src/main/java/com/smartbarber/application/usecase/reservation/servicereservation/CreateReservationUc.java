package com.smartbarber.application.usecase.reservation.servicereservation;

import com.smartbarber.application.command.in.reservation.ReservationCommand;
import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.reservation.ReservationMessageExceptions;
import com.smartbarber.domain.exceptions.service.ServiceMessageExceptions;
import com.smartbarber.domain.model.customer.Customer;
import com.smartbarber.domain.model.notification.Notification;
import com.smartbarber.domain.model.reservation.Reservation;
import com.smartbarber.domain.model.reservation.ServiceReservation;
import com.smartbarber.domain.model.service.Service;
import com.smartbarber.domain.port.CustomerPort;
import com.smartbarber.domain.port.NotificationPort;
import com.smartbarber.domain.port.ServicePort;
import com.smartbarber.domain.port.reservation.ReservationRepositoryPort;
import com.smartbarber.domain.port.reservation.ServiceReservationPort;
import com.smartbarber.infrastructure.drivenadapter.email.EmailService;
import com.smartbarber.application.usecase.nonworkingday.BarbershopCalendar;
import com.smartbarber.application.usecase.nonworkingday.FindNonWorkingDaysUC;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Log4j2
public class CreateReservationUc {

    private final ServiceReservationPort serviceReservationPort;
    private final ReservationRepositoryPort reservationRepositoryPort;
    private final ServicePort servicePort;
    private final TransactionalOperator transactionalOperator;
    private final NotificationPort notificationPort;
    private final CustomerPort customerPort;
    private final EmailService emailService;
    private final FindNonWorkingDaysUC findNonWorkingDaysUC;
    private final BarbershopCalendar barbershopCalendar;

    public Mono<Reservation> createReservation(ReservationCommand command) {
        System.out.println("Reservation command: " + command);
        return loadServices(command.reservationServiceId())
                .flatMap(services -> {
                    Integer duration = getDuration(services);

                    ReservationCommand newReservationCommand =
                            ReservationCommand.builder()
                                    .id(command.id())
                                    .barbershopId(command.barbershopId())
                                    .customerId(command.customerId())
                                    .employeeId(command.employeeId())
                                    .reservationServiceId(command.reservationServiceId())
                                    .reservationType(command.reservationType())
                                    .status(command.status())
                                    .startTime(command.startTime())
                                    .endTime(command.startTime().plus(duration, ChronoUnit.MINUTES))
                                    .notes(command.notes())
                                    .creationDate(command.creationDate())
                                    .updatedDate(command.updatedDate())
                                    .build();

                    return validateReservation(newReservationCommand)
                            .thenReturn(services);
                })
                .flatMap(services -> createReservation(command, services))
                .as(transactionalOperator::transactional);
    }

    private Mono<Void> validateReservation(ReservationCommand command) {
        Mono<Void> employeeValidation =
                reservationRepositoryPort.existsByEmployeeAndRange(
                                command.employeeId(),
                                command.startTime(),
                                command.endTime())
                        .flatMap(exists -> Boolean.TRUE.equals(exists)
                                ? Mono.error(new BusinessExceptions(
                                ReservationMessageExceptions.EMPLOYEE_NOT_AVAILABLE))
                                : Mono.empty());

        Mono<Void> customerValidation =
                reservationRepositoryPort.existsByCustomerAndRange(
                                command.customerId(),
                                command.startTime(),
                                command.endTime())
                        .flatMap(exists -> Boolean.TRUE.equals(exists)
                                ? Mono.error(new BusinessExceptions(
                                ReservationMessageExceptions.RESERVATION_ALREADY_EXISTS))
                                : Mono.empty());

        // La barbería no atiende en sus días no laborables (festivos, cierres).
        Mono<Void> openDayValidation = command.startTime() == null
                ? Mono.empty()
                : findNonWorkingDaysUC.ensureOpen(
                        command.barbershopId(),
                        barbershopCalendar.dateOf(command.startTime()));

        return Mono.when(openDayValidation, employeeValidation, customerValidation);
    }

    private Mono<List<Service>> loadServices(List<Integer> serviceIds) {
        if (serviceIds == null || serviceIds.isEmpty()) {
            return Mono.error(new BusinessExceptions(
                    ServiceMessageExceptions.SERVICE_NOT_FOUND));
        }

        return Flux.fromIterable(serviceIds)
                .flatMap(id -> servicePort.findById(id)
                        .switchIfEmpty(Mono.error(new BusinessExceptions(
                                ServiceMessageExceptions.SERVICE_NOT_FOUND))))
                .collectList();
    }

    private Mono<Reservation> createReservation(
            ReservationCommand command,
            List<Service> services) {

        Integer duration = getDuration(services);

        Reservation reservation = Reservation.createReservation(
                command.customerId(),
                command.barbershopId(),
                command.employeeId(),
                duration,
                command.startTime(),
                command.startTime().plus(duration, ChronoUnit.MINUTES),
                command.notes()
        );

        System.out.println("DOMAIN: " + reservation.toString());

        return reservationRepositoryPort.save(reservation)
                .doOnNext(saved -> log.info("Reservation saved: {}", saved.getId()))
                .flatMap(savedReservation ->
                        createServiceReservations(savedReservation, services)
                                .thenReturn(savedReservation))
                .flatMap(savedReservation ->
                        createNotificationAndSendEmail(savedReservation)
                                .thenReturn(savedReservation))
                .doOnNext(saved -> log.info(
                        "Reservation creation flow completed: {}", saved.getId()));
    }

    private Mono<Void> createNotificationAndSendEmail(Reservation reservation) {
        return customerPort.findById(reservation.getCustomerId())
                .switchIfEmpty(Mono.error(
                        new IllegalStateException("Customer not found")))
                .flatMap(customer -> {
                    String title = "Solicitud de reserva recibida";
                    String content = buildReservationEmail(customer, reservation);

                    Notification notification = Notification.createNotification(
                            customer.getId(),
                            reservation.getId(),
                            title,
                            content,
                            "EMAIL",
                            "RESERVATION_CREATED",
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
                                            customer.getEmail()));
                })
                .onErrorResume(error -> {
                    log.error(
                            "Could not create or process notification for reservation {}",
                            reservation.getId(),
                            error);
                    return Mono.empty();
                });
    }

    private Mono<Void> sendAndUpdateNotification(
            Notification notification,
            String customerEmail) {

        return emailService.sendEmail(
                        customerEmail,
                        notification.getTitle(),
                        notification.getContent())
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
                                    sentNotification)
                            .doOnSuccess(updated -> log.info(
                                    "Notification {} sent successfully",
                                    notification.getId()))
                            .then();
                }))
                .onErrorResume(error -> {
                    log.error(
                            "Email could not be sent for notification {}",
                            notification.getId(),
                            error);

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
                                    failedNotification)
                            .doOnError(updateError -> log.error(
                                    "Could not update failed notification {}",
                                    notification.getId(),
                                    updateError))
                            .onErrorResume(updateError -> Mono.empty())
                            .then();
                });
    }

    private String buildReservationEmail(
            Customer customer,
            Reservation reservation
    ) {
        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", new Locale("es", "CO"))
                        .withZone(ZoneId.of("America/Bogota"));

        DateTimeFormatter timeFormatter =
                DateTimeFormatter.ofPattern("h:mm a", new Locale("es", "CO"))
                        .withZone(ZoneId.of("America/Bogota"));
        String notes = reservation.getNotes() != null
                && !reservation.getNotes().isBlank()
                ? reservation.getNotes()
                : "Sin observaciones adicionales.";

        return "Hola, " + customer.getName() + ":\n\n"
                + "¡Gracias por elegir SmartBarber!\n\n"
                + "Hemos recibido tu solicitud de reserva correctamente. "
                + "Nos alegra que nos tengas en cuenta para tu próximo servicio.\n\n"
                + "DETALLES DE TU RESERVA\n\n"
                + "Fecha: " + formatter.format(reservation.getStartTime()) + "\n"
                + "Hora de inicio: " + timeFormatter.format(reservation.getStartTime()) + "\n"
                + "Hora estimada de finalización: "
                + timeFormatter.format(reservation.getEndTime()) + "\n"
                + "Estado: Pendiente de confirmación.\n\n"
                + "Observaciones: " + notes + "\n\n"
                + "Tu reserva se encuentra pendiente de confirmación. "
                + "Te notificaremos por este medio cuando su estado sea actualizado.\n\n"
                + "Si tienes alguna pregunta o necesitas realizar algún cambio, "
                + "no dudes en comunicarte con nosotros.\n\n"
                + "¡Gracias por confiar en SmartBarber!\n\n"
                + "Saludos cordiales,\n"
                + "El equipo de SmartBarber";
    }

    private Mono<Void> createServiceReservations(
            Reservation reservation,
            List<Service> services) {

        return Flux.fromIterable(services)
                .flatMap(service ->
                        serviceReservationPort.save(
                                ServiceReservation.createServiceReservation(
                                        reservation.getId(),
                                        service.getId(),
                                        service.getDuration()
                                )))
                .then();
    }

    private Integer getDuration(List<Service> services) {
        return services.stream()
                .map(Service::getDuration)
                .reduce(0, Integer::sum);
    }
}
