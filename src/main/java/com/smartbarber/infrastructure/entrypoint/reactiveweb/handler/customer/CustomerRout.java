package com.smartbarber.infrastructure.entrypoint.reactiveweb.handler.customer;

import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.*;

import static com.smartbarber.infrastructure.entrypoint.utils.constants.HandlerConstant.*;

@Configuration
@AllArgsConstructor
public class CustomerRout {

    private final CustomerHandler customerHandler;

    @Bean
    public RouterFunction<ServerResponse> clientRutas(){
        return RouterFunctions.route(RequestPredicates.POST(PUBLIC + CUSTOMER_SERVICE + "/create-customer"), customerHandler::createCustomer)
                .andRoute(RequestPredicates.POST(CUSTOMER_SERVICE + "/onboarding"), customerHandler::onBoardCustomer)
                .andRoute(RequestPredicates.GET(CUSTOMER_SERVICE + "/name/{name}"), customerHandler::findCustomerByName)
                .andRoute(RequestPredicates.GET(CUSTOMER_SERVICE + "/document/{document}"), customerHandler::findByCustomerByDocument)
                .andRoute(RequestPredicates.GET(CUSTOMER_SERVICE + "/id/{id}"), customerHandler::findCustomerById)
                .andRoute(RequestPredicates.PUT(CUSTOMER_SERVICE + "/update-customer/{id}"), customerHandler::updateCustomer)
                .andRoute(RequestPredicates.PATCH(CUSTOMER_SERVICE + "/block-customer/{id}"),customerHandler::blockCustomer);
    }
}
  /*"/ws/virtual-queue/**",
                                "/ws/payment/**",
                                "/auth/**",
                                "/reservation-service/**",
                                "/schedule-service/**",
                                "/services-service/**",
                                "/customer-service/**",
                                "/reservation-review-service/**",
                                "/notification-service/**",
                                "/public/**"*/