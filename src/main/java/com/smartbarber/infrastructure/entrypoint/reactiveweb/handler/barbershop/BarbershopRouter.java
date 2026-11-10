package com.smartbarber.infrastructure.entrypoint.reactiveweb.handler.barbershop;

import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.*;

import static com.smartbarber.infrastructure.entrypoint.utils.constants.HandlerConstant.BARBER_SERVICE;
import static com.smartbarber.infrastructure.entrypoint.utils.constants.HandlerConstant.PUBLIC;

@Configuration
@AllArgsConstructor
public class BarbershopRouter {

    private final BarbershopHandler barbershopHandler;

    @Bean
    public RouterFunction<ServerResponse> routerBarbershop(){
        System.out.println("Router Barbershop");
        return RouterFunctions.route(RequestPredicates.POST(PUBLIC + BARBER_SERVICE + "/create-barber"), barbershopHandler::createBarbershop)
                .andRoute(RequestPredicates.POST(BARBER_SERVICE + "/onboarding"), barbershopHandler::onBoardingBarber)
                .andRoute(RequestPredicates.GET(BARBER_SERVICE + "/name/{name}"), barbershopHandler::findBarbershopByName)
                .andRoute(RequestPredicates.GET(BARBER_SERVICE + "/find-all"), barbershopHandler::findAllBarberShop)
                .andRoute(RequestPredicates.GET(BARBER_SERVICE + "/company-name/{companyName}"), barbershopHandler::findBarbershopByCompanyName)
                .andRoute(RequestPredicates.GET(BARBER_SERVICE + "/document/{document}"), barbershopHandler::findByBarbershopByDocument)
                .andRoute(RequestPredicates.GET(BARBER_SERVICE + "/id/{id}"), barbershopHandler::findBarbershopById)
                .andRoute(RequestPredicates.PUT(BARBER_SERVICE + "/update-barber/{id}"), barbershopHandler::updateBarbershop);
    }
}
