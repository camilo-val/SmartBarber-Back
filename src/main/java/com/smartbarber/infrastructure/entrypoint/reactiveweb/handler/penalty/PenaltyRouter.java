package com.smartbarber.infrastructure.entrypoint.reactiveweb.handler.penalty;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static com.smartbarber.infrastructure.entrypoint.utils.constants.HandlerConstant.PENALTY_SERVICE;

@Configuration
@RequiredArgsConstructor
public class PenaltyRouter {

    private final PenaltyHandler handler;

    @Bean
    public RouterFunction<ServerResponse> penaltyRoutes() {
        return RouterFunctions
                .route(RequestPredicates.GET(PENALTY_SERVICE + "/policy/barbershop/{barbershopId}"), handler::policyOfBarbershop)
                .andRoute(RequestPredicates.GET(PENALTY_SERVICE + "/policy/mine"), handler::myPolicy)
                .andRoute(RequestPredicates.PUT(PENALTY_SERVICE + "/policy/mine"), handler::updateMyPolicy)
                .andRoute(RequestPredicates.GET(PENALTY_SERVICE + "/me"), handler::myStandings)
                .andRoute(RequestPredicates.GET(PENALTY_SERVICE + "/me/barbershop/{barbershopId}"), handler::myStanding)
                .andRoute(RequestPredicates.GET(PENALTY_SERVICE + "/customers"), handler::customers)
                .andRoute(RequestPredicates.PATCH(PENALTY_SERVICE + "/restrictions/{id}/lift"), handler::lift);
    }
}
