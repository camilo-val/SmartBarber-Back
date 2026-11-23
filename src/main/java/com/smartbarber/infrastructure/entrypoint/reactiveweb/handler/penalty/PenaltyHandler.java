package com.smartbarber.infrastructure.entrypoint.reactiveweb.handler.penalty;

import com.smartbarber.application.usecase.penalty.CustomerPenaltyUC;
import com.smartbarber.application.usecase.penalty.PenaltyPolicyUC;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.penalty.PenaltyPolicyRqDto;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.penalty.PenaltyPolicyRsDto;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.penalty.PenaltyStandingRsDto;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.penalty.ReasonRqDto;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.penalty.RestrictionLiftRsDto;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.exception.TechnicalExceptions;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.exception.TechnicalMessageExceptions;
import com.smartbarber.infrastructure.entrypoint.utils.validateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Política de penalización, estado de los clientes y restricciones. */
@Component
@RequiredArgsConstructor
public class PenaltyHandler {

    private final PenaltyPolicyUC penaltyPolicyUC;
    private final CustomerPenaltyUC customerPenaltyUC;
    private final validateRequest validateRequest;

    /** GET /penalty-service/policy/barbershop/{barbershopId}: la política que ve el cliente antes de reservar. */
    public Mono<ServerResponse> policyOfBarbershop(ServerRequest request) {
        return uuid(request, "barbershopId")
                .flatMap(penaltyPolicyUC::forBarbershop)
                .map(PenaltyPolicyRsDto::from)
                .flatMap(policy -> ServerResponse.ok().bodyValue(policy));
    }

    /** GET /penalty-service/policy/mine (ADMIN) */
    public Mono<ServerResponse> myPolicy(ServerRequest request) {
        return penaltyPolicyUC.forCurrentAdmin()
                .map(PenaltyPolicyRsDto::from)
                .flatMap(policy -> ServerResponse.ok().bodyValue(policy));
    }

    /** PUT /penalty-service/policy/mine (ADMIN) */
    public Mono<ServerResponse> updateMyPolicy(ServerRequest request) {
        return request.bodyToMono(PenaltyPolicyRqDto.class)
                .switchIfEmpty(Mono.error(() -> new TechnicalExceptions(TechnicalMessageExceptions.BAD_REQUEST)))
                .doOnNext(validateRequest::validate)
                .flatMap(body -> penaltyPolicyUC.update(body.toSettings()))
                .map(PenaltyPolicyRsDto::from)
                .flatMap(policy -> ServerResponse.ok().bodyValue(policy));
    }

    /** GET /penalty-service/me (CUSTOMER): barberías con inasistencias vigentes o restricción (RF-85). */
    public Mono<ServerResponse> myStandings(ServerRequest request) {
        return customerPenaltyUC.myStandings()
                .map(views -> views.stream().map(PenaltyStandingRsDto::from).toList())
                .flatMap(views -> ServerResponse.ok().bodyValue(views));
    }

    /** GET /penalty-service/me/barbershop/{barbershopId} (CUSTOMER): estado antes de reservar. */
    public Mono<ServerResponse> myStanding(ServerRequest request) {
        return uuid(request, "barbershopId")
                .flatMap(customerPenaltyUC::myStanding)
                .map(PenaltyStandingRsDto::from)
                .flatMap(view -> ServerResponse.ok().bodyValue(view));
    }

    /** GET /penalty-service/customers (ADMIN): clientes en advertencia y restringidos. */
    public Mono<ServerResponse> customers(ServerRequest request) {
        return customerPenaltyUC.forCurrentAdmin()
                .map(views -> views.stream().map(PenaltyStandingRsDto::from).toList())
                .flatMap(views -> ServerResponse.ok().bodyValue(views));
    }

    /** PATCH /penalty-service/restrictions/{id}/lift (ADMIN)  { "reason": "..." } */
    public Mono<ServerResponse> lift(ServerRequest request) {
        return uuid(request, "id")
                .flatMap(id -> request.bodyToMono(ReasonRqDto.class)
                        .switchIfEmpty(Mono.error(() -> new TechnicalExceptions(TechnicalMessageExceptions.BAD_REQUEST)))
                        .doOnNext(validateRequest::validate)
                        .flatMap(body -> customerPenaltyUC.lift(id, body.reason())))
                .map(RestrictionLiftRsDto::from)
                .flatMap(lifted -> ServerResponse.ok().bodyValue(lifted));
    }

    private static Mono<UUID> uuid(ServerRequest request, String name) {
        return Mono.fromCallable(() -> UUID.fromString(request.pathVariable(name)))
                .onErrorMap(IllegalArgumentException.class,
                        error -> new TechnicalExceptions(TechnicalMessageExceptions.BAD_REQUEST));
    }
}
