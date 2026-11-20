package com.smartbarber.application.usecase.queue;

import com.smartbarber.domain.exceptions.barberqueue.BarberQueueMessageExceptions;
import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.port.reservation.ReservationRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class QueueAvailabilityService {

    private final ReservationRepositoryPort reservationRepositoryPort;

    public Mono<Void> validateAvailability(
            UUID employeeId,
            Integer newDuration
    ) {

        if (employeeId == null) {
            return Mono.error(
                    new BusinessExceptions(
                                BarberQueueMessageExceptions.INVALID_QUEUE_DATA)
            );
        }

        if (newDuration == null || newDuration <= 0) {
            return Mono.error(
                    new BusinessExceptions(
                                BarberQueueMessageExceptions.INVALID_QUEUE_DATA)
            );
        }

        return Mono.defer(() -> {

            OffsetDateTime now = OffsetDateTime.now();

            Mono<Integer> queueDurationMono =
                    reservationRepositoryPort
                            .calculateRemainingQueueDuration(
                                    employeeId,
                                    now
                            )
                            .defaultIfEmpty(0);

            Mono<OffsetDateTime> nextReservationMono =
                    reservationRepositoryPort
                            .findNextScheduledReservationStart(
                                    employeeId,
                                    now
                            );

            return Mono.zip(
                            queueDurationMono,
                            nextReservationMono.defaultIfEmpty(OffsetDateTime.MAX)
                    )
                    .flatMap(tuple -> {

                        int pendingMinutes = tuple.getT1();
                        OffsetDateTime nextReservationStart = tuple.getT2();

                        OffsetDateTime estimatedFinish =
                                now.plus(
                                        Duration.ofMinutes(
                                                (long) pendingMinutes
                                                        + newDuration
                                        )
                                );

                        if (!estimatedFinish.isBefore(nextReservationStart)) {
                            return Mono.error(
                                    new BusinessExceptions(BarberQueueMessageExceptions.NOT_ENOUGH_TIME)
                            );
                        }

                        return Mono.empty();
                    });
        });
    }
}