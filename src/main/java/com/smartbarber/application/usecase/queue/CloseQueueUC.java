package com.smartbarber.application.usecase.queue;

import com.smartbarber.domain.exceptions.barberqueue.BarberQueueMessageExceptions;
import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.enums.NotificationType;
import com.smartbarber.domain.model.barberqueue.BarberQueue;
import com.smartbarber.domain.model.barbershop.Barbershop;
import com.smartbarber.domain.model.barbershop.BarbershopQueueResponse;
import com.smartbarber.domain.model.employee.Employee;
import com.smartbarber.domain.model.employee.EmployeeQueueResponse;
import com.smartbarber.domain.port.BarberShopRepositoryPort;
import com.smartbarber.domain.port.EmployeePort;
import com.smartbarber.domain.port.queue.BarberQueuePort;
import com.smartbarber.domain.port.queue.notification.NotificationEventPort;
import com.smartbarber.infrastructure.drivenadapter.postgres.entity.BarberQueueEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CloseQueueUC {

    private final BarberQueuePort barberQueuePort;
    private final QueueBroadcastService queueBroadcastService;
    private final TransactionalOperator transactionalOperator;
    private final NotificationEventPort notificationEventPort;
    private final BarberShopRepositoryPort barberShopRepositoryPort;
    private final EmployeePort employeePort;

    public Mono<Void> execute(UUID employeeId) {

        return barberQueuePort.findByEmployeeIdForUpdate(employeeId)
                .doOnNext(queue ->
                        log.info("Found queue for employeeId: {}", employeeId))
                .switchIfEmpty(
                        Mono.error(
                                new BusinessExceptions(
                                BarberQueueMessageExceptions.THE_QUEUE_BABER_DOES_NOT_CONFIGURATION)
                        )
                )
                .flatMap(queue ->
                        barberShopRepositoryPort.findById(queue.getBarbershopId())
                                .doOnNext(barbershop ->
                                        log.info("Found barbershop for queue: {}", barbershop))
                                .flatMap(barbershop ->
                                        employeePort.findById(employeeId)
                                                .doOnNext(employee ->
                                                        log.info("Found employee for queue: {}", employee))
                                                .flatMap(employee ->
                                                        barberQueuePort.updateAcceptingClients(
                                                                        employeeId,
                                                                        false
                                                                )
                                                                .doOnNext(rows ->
                                                                        log.info(
                                                                                "Updated accepting clients for employeeId: {} rows: {}",
                                                                                employeeId,
                                                                                rows
                                                                        ))
                                                                .flatMap(rows -> {
                                                                    if (rows <= 0) {
                                                                        return Mono.error(
                                                                                new BusinessExceptions(
                                BarberQueueMessageExceptions.QUEUE_OPERATION_FAILED)
                                                                        );
                                                                    }

                                                                    return Mono.fromCallable(
                                                                            () -> build(
                                                                                    barbershop,
                                                                                    queue,
                                                                                    employee
                                                                            )
                                                                    );
                                                                })
                                                )
                                )
                )
                .as(transactionalOperator::transactional)
                .flatMap(notificationEventPort::publish);
    }

    private BarbershopQueueResponse build(
            Barbershop barbershop,
            Employee employee
    ) {

        return BarbershopQueueResponse.builder()
                .id(barbershop.getId())
                .name(barbershop.getName())
                .location(barbershop.getLocation())
                .employee(
                        EmployeeQueueResponse.builder()
                                .id(employee.getId())
                                .name(employee.getName())
                                .build()
                )
                .build();
    }

    private BarberQueue build(
            Barbershop barbershop,
            BarberQueueEntity entity,
            Employee employee
    ) {

        return BarberQueue.builder()
                .type(NotificationType.QUEUE_CLOSED)
                .entity(entity)
                .barbershop(
                        build(
                                barbershop,
                                employee
                        )
                )
                .build();
    }
}