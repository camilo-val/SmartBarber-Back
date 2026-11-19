package com.smartbarber.infrastructure.entrypoint.reactiveweb.handler.nonworkingday;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static com.smartbarber.infrastructure.entrypoint.utils.constants.HandlerConstant.NON_WORKING_DAY_SERVICE;

@Configuration
@RequiredArgsConstructor
public class NonWorkingDayRouter {

    private final NonWorkingDayHandler handler;

    @Bean
    public RouterFunction<ServerResponse> nonWorkingDayRoutes() {
        return RouterFunctions
                .route(RequestPredicates.GET(NON_WORKING_DAY_SERVICE + "/barbershop/{barbershopId}"), handler::findByBarbershop)
                .andRoute(RequestPredicates.POST(NON_WORKING_DAY_SERVICE), handler::create)
                .andRoute(RequestPredicates.DELETE(NON_WORKING_DAY_SERVICE + "/{id}"), handler::delete);
    }
}
