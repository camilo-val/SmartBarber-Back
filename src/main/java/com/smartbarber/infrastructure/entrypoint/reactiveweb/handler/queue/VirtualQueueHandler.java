
package com.smartbarber.infrastructure.entrypoint.reactiveweb.handler.queue;

import com.smartbarber.application.command.in.reservation.ReservationCommand;
import com.smartbarber.application.usecase.queue.*;
import com.smartbarber.application.usecase.reservation.servicereservation.CreateReservationQueueUc;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class VirtualQueueHandler {

    private final OpenQueueUC openQueueUC;
    private final NextCustomerUC nextCustomerUC;
    private final CloseQueueUC closeQueueUC;
    private final ConsultStatusQueue consultStatusQueue;
    private final CreateReservationQueueUc createReservationQueueUc;
    private final QueueBroadcastService queueBroadcastService;
    private final CompleteCurrentTurnUC completeCurrentTurnUC;

    public Mono<ServerResponse> openQueue(ServerRequest request) {
        UUID employeeId = UUID.fromString(
                request.pathVariable("employeeId")
        );

        return openQueueUC.execute(employeeId)
                .then(ServerResponse.ok().bodyValue(
                        Map.of(
                                "message", "Cola abierta correctamente",
                                "employeeId", employeeId
                        )
                ));
    }

    public Mono<ServerResponse> closeQueue(ServerRequest request) {
        UUID employeeId = UUID.fromString(
                request.pathVariable("employeeId")
        );

        return closeQueueUC.execute(employeeId)
                .then(ServerResponse.ok().bodyValue(
                        Map.of(
                                "message", "Cola cerrada para nuevos clientes",
                                "employeeId", employeeId
                        )
                ));
    }

    public Mono<ServerResponse> nextCustomer(ServerRequest request) {
        UUID employeeId = UUID.fromString(
                request.pathVariable("employeeId")
        );

        return nextCustomerUC.execute(employeeId)
                .flatMap(reservation ->
                        ServerResponse.ok().bodyValue(reservation)
                );
    }

    public Mono<ServerResponse> joinQueue(ServerRequest request) {
        return request.bodyToMono(ReservationCommand.class)
                .flatMap(createReservationQueueUc::createReservation)
                .flatMap(reservation ->
                        ServerResponse.created(
                                URI.create(
                                        "/reservation-service/"
                                                + reservation.getId()
                                )
                        ).bodyValue(reservation)
                );
    }

    public Mono<ServerResponse> completeCurrentTurn(
            ServerRequest request
    ) {
        UUID employeeId = UUID.fromString(
                request.pathVariable("employeeId")
        );

        return completeCurrentTurnUC.execute(employeeId)
                .flatMap(reservation ->
                        ServerResponse.ok().bodyValue(reservation)
                );
    }

    public Mono<ServerResponse> getQueue(ServerRequest request) {
        UUID employeeId = UUID.fromString(
                request.pathVariable("employeeId")
        );

        // El personal ve la cola completa; un cliente, sin datos de los demás.
        return queueBroadcastService.getSnapshotForCurrentUser(employeeId)
                .flatMap(snapshot ->
                        ServerResponse.ok().bodyValue(snapshot)
                );
    }
    public Mono<ServerResponse> getQueueStatus(ServerRequest request) {
        UUID employeeId = UUID.fromString(
                request.pathVariable("employeeId")
        );

        return consultStatusQueue.currentStatusQueue(employeeId)
                .flatMap(status ->
                        ServerResponse.ok().bodyValue(status)
                );
    }
}
