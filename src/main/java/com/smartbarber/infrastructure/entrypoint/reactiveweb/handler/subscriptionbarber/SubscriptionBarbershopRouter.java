package com.smartbarber.infrastructure.entrypoint.reactiveweb.handler.subscriptionbarber;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.*;

import static com.smartbarber.infrastructure.entrypoint.utils.constants.HandlerConstant.SUBSCRIPTION_BARBER_SERVICE;

@Configuration
public class SubscriptionBarbershopRouter {

    @Bean
    public RouterFunction<ServerResponse> subscriptionBarbershopRoutes(SubscriptionBarbershopHandler handler) {
        return RouterFunctions.route(RequestPredicates.POST(SUBSCRIPTION_BARBER_SERVICE+ "/create"), handler::createSubscriptionForBarber)
                .andRoute(RequestPredicates.PUT(SUBSCRIPTION_BARBER_SERVICE+ "/renew"), handler::renewSubscriptionForBarber)
                .andRoute(RequestPredicates.GET(SUBSCRIPTION_BARBER_SERVICE+ "/actual-subscription/{barberId}"), handler::findActualSubscriptionByBarberId)
                .andRoute(RequestPredicates.GET(SUBSCRIPTION_BARBER_SERVICE+ "/subscription-history/{barberId}"), handler::findSubscriptionHistoryByBarberId);
                //.andRoute(RequestPredicates.PUT(SUBSCRIPTION_BARBER_SERVICE+ "/renew"), handler::renewSubscriptionForBarber);
    }

}
