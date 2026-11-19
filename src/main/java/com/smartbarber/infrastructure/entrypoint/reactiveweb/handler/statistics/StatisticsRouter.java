package com.smartbarber.infrastructure.entrypoint.reactiveweb.handler.statistics;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static com.smartbarber.infrastructure.entrypoint.utils.constants.HandlerConstant.STATISTICS_SERVICE;

@Configuration
@RequiredArgsConstructor
public class StatisticsRouter {

    private final StatisticsHandler handler;

    @Bean
    public RouterFunction<ServerResponse> statisticsRoutes() {
        return RouterFunctions.route(
                RequestPredicates.GET(STATISTICS_SERVICE + "/dashboard"), handler::getDashboard);
    }
}
