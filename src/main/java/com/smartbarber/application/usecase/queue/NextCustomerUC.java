
package com.smartbarber.application.usecase.queue;

import com.smartbarber.domain.exceptions.barberqueue.BarberQueueMessageExceptions;
import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.enums.NotificationType;
import com.smartbarber.domain.model.reservation.Reservation;
import com.smartbarber.domain.port.queue.BarberQueuePort;
import com.smartbarber.domain.port.reservation.ReservationRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class NextCustomerUC {

    private final BarberQueuePort barberQueuePort;
    private final ReservationRepositoryPort reservationRepositoryPort;
    private final QueueBroadcastService queueBroadcastService;
    private final TransactionalOperator transactionalOperator;

    public Mono<Reservation> execute(UUID employeeId) {
        Mono<Reservation> operation = barberQueuePort
                .findByEmployeeIdForUpdate(employeeId)
                .switchIfEmpty(Mono.error(
                        new BusinessExceptions(
                                BarberQueueMessageExceptions.THE_QUEUE_BABER_DOES_NOT_CONFIGURATION)
                ))
                .flatMap(queue ->
                        reservationRepositoryPort
                                .findCurrentQueueTurn(employeeId)
                                .hasElement()
                                .flatMap(hasCurrent -> {
                                    if (hasCurrent) {
                                        return Mono.error(
                                                new BusinessExceptions(
                                BarberQueueMessageExceptions.TURN_ALREADY_IN_PROGRESS)
                                        );
                                    }

                                    return reservationRepositoryPort
                                            .findWaitingQueueByEmployeeId(
                                                    employeeId
                                            )
                                            .next()
                                            .switchIfEmpty(Mono.error(
                                                    new BusinessExceptions(
                                BarberQueueMessageExceptions.NO_WAITING_CUSTOMERS)
                                            ))
                                            .flatMap(waiting ->
                                                    reservationRepositoryPort
                                                            .save(waiting
                                                                    .iniciarAtencionDesdeCola())
                                            );
                                })
                );

        return operation
                .as(transactionalOperator::transactional)
                .flatMap(reservation ->
                        queueBroadcastService
                                .publish(employeeId, NotificationType.QUEUE_NEXT_CUSTOMER)
                                .thenReturn(reservation)
                );
    }
}
