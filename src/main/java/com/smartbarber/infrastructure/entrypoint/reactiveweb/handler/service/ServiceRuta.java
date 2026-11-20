package com.smartbarber.infrastructure.entrypoint.reactiveweb.handler.service;

import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.*;

import static com.smartbarber.infrastructure.entrypoint.utils.constants.HandlerConstant.SERVICES_SERVICE;

@Configuration
@AllArgsConstructor
public class ServiceRuta {

    private final ServiceHandler serviceHandler;

    @Bean
    public RouterFunction<ServerResponse> serviceRutas(){
        return RouterFunctions.route(RequestPredicates.POST(SERVICES_SERVICE + "/crear-servicio"),serviceHandler::createService)
                .andRoute(RequestPredicates.GET(SERVICES_SERVICE + "/name/{name}"),serviceHandler::findServiceByName)
                .andRoute(RequestPredicates.GET(SERVICES_SERVICE + "/id/{id}"),serviceHandler::findServiceById)
                .andRoute(RequestPredicates.PUT(SERVICES_SERVICE + "/actualizar-service/{id}"),serviceHandler::updateService)
                .andRoute(RequestPredicates.GET(SERVICES_SERVICE + "/barberia/{barberiaId}"), serviceHandler::findServicesByBarberiaId)
                .andRoute(RequestPredicates.PATCH(SERVICES_SERVICE + "/{id}/status"), serviceHandler::changeServiceStatus)
                .andRoute(RequestPredicates.DELETE(SERVICES_SERVICE + "/{id}"), serviceHandler::deleteService);
    }
}
