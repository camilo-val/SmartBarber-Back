package com.smartbarber.infrastructure.entrypoint.reactiveweb.config;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifica las reglas por rol de {@link SecurityConfig} sin Firebase: el JwtFilter
 * se reemplaza por uno que toma el rol de la cabecera X-Test-Role, y todas las rutas
 * responden 200 si la seguridad las deja pasar.
 */
class SecurityConfigRulesTest {

    private static final String ID = "3f0c1d2e-1111-4a5b-9c8d-123456789abc";

    private static AnnotationConfigApplicationContext context;
    private static WebTestClient client;

    @Configuration
    @EnableWebFlux
    @Import(SecurityConfig.class)
    static class TestConfig {

        @Bean
        JwtFilter jwtFilter() {
            JwtFilter filter = mock(JwtFilter.class);
            when(filter.filter(any(), any())).thenAnswer(invocation -> {
                ServerWebExchange exchange = invocation.getArgument(0);
                WebFilterChain chain = invocation.getArgument(1);
                String role = exchange.getRequest().getHeaders().getFirst("X-Test-Role");
                if (role == null) {
                    return chain.filter(exchange);
                }
                var authentication = new UsernamePasswordAuthenticationToken(
                        "user", null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                return chain.filter(exchange)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
            });
            return filter;
        }

        @Bean
        RouterFunction<ServerResponse> everyRoute() {
            return RouterFunctions.route(RequestPredicates.all(), request -> ServerResponse.ok().build());
        }
    }

    @BeforeAll
    static void start() {
        context = new AnnotationConfigApplicationContext(TestConfig.class);
        client = WebTestClient.bindToApplicationContext(context).build();
    }

    @AfterAll
    static void stop() {
        context.close();
    }

    @ParameterizedTest(name = "{1} {2} como {0} → {3}")
    @CsvSource({
            // Rutas públicas
            "NONE,     POST,   /barber-service/onboarding,                      200",
            "NONE,     POST,   /customer-service/onboarding,                    200",
            "NONE,     POST,   /auth/verify,                                    200",
            "NONE,     GET,    /barber-service/id/ID,                           200",
            // Sin sesión no se entra al resto
            "NONE,     GET,    /reservation-service/customer/ID,                401",
            "NONE,     GET,    /statistics-service/dashboard,                   401",
            // Días no laborables
            "CUSTOMER, GET,    /non-working-day-service/barbershop/ID,          200",
            "BARBER,   GET,    /non-working-day-service/barbershop/ID,          200",
            "ADMIN,    POST,   /non-working-day-service,                        200",
            "BARBER,   POST,   /non-working-day-service,                        403",
            "ADMIN,    DELETE, /non-working-day-service/ID,                     200",
            "CUSTOMER, DELETE, /non-working-day-service/ID,                     403",
            // Equipo y bloqueo de barberos
            "ADMIN,    GET,    /employee-service/team,                          200",
            "BARBER,   GET,    /employee-service/team,                          403",
            "ADMIN,    PATCH,  /employee-service/block-employee/ID,             200",
            "ADMIN,    PATCH,  /employee-service/unblock-employee/ID,           200",
            "BARBER,   PATCH,  /employee-service/unblock-employee/ID,           403",
            "CUSTOMER, PATCH,  /employee-service/block-employee/ID,             403",
            // Estadísticas
            "ADMIN,    GET,    /statistics-service/dashboard,                   200",
            "BARBER,   GET,    /statistics-service/dashboard,                   403",
            "CUSTOMER, GET,    /statistics-service/dashboard,                   403",
            // Rutas que usa el front con cada rol
            "CUSTOMER, GET,    /barber-service/find-all,                        200",
            "CUSTOMER, GET,    /customer-service/id/ID,                         200",
            "CUSTOMER, PUT,    /customer-service/update-customer/ID,            200",
            "CUSTOMER, POST,   /reservation-service/create,                     200",
            "CUSTOMER, GET,    /reservation-service/customer/ID,                200",
            "CUSTOMER, GET,    /public/employee-service/barbershop/ID/bookable, 200",
            "CUSTOMER, GET,    /reservation-service/employee/ID,                200",
            "CUSTOMER, GET,    /reservation-service/barber/ID,                  200",
            "CUSTOMER, GET,    /public/employee-service/barbershop/ID,          403",
            "BARBER,   GET,    /public/employee-service/barbershop/ID,          200",
            "BARBER,   PATCH,  /reservation-service/ID/complete,                200",
            "ADMIN,    PATCH,  /reservation-service/ID/no-show,                 200",
            "CUSTOMER, PATCH,  /reservation-service/ID/complete,                403",
            "CUSTOMER, PATCH,  /reservation-service/ID/no-show,                 403",
            "CUSTOMER, PATCH,  /reservation-service/ID/reschedule,              200",
            "ADMIN,    PATCH,  /services-service/ID/status,                     200",
            "BARBER,   PATCH,  /services-service/ID/status,                     403",
            "ADMIN,    DELETE, /services-service/ID,                            200",
            "CUSTOMER, DELETE, /services-service/ID,                            403",
            "ADMIN,    GET,    /barber-service/email/x,                         200",
            "CUSTOMER, GET,    /barber-service/email/x,                         403",
            "BARBER,   PATCH,  /reservation-service/ID/reschedule,              403",
            "CUSTOMER, GET,    /services-service/barberia/ID,                   200",
            "CUSTOMER, GET,    /schedule-service/empleado/ID,                   200",
            "CUSTOMER, POST,   /api/v1/queue/join,                              200",
            "BARBER,   GET,    /employee-service/id/ID,                         200",
            "BARBER,   GET,    /reservation-service/employee/ID,                200",
            "BARBER,   POST,   /api/v1/queue/ID/open,                           200",
            "ADMIN,    GET,    /employee-service/id/ID,                         200",
            "ADMIN,    GET,    /employee-service/nombre/x,                      200",
            "ADMIN,    PUT,    /barber-service/update-barber/ID,                200",
            "ADMIN,    POST,   /services-service/crear-servicio,                200",
            "ADMIN,    POST,   /schedule-service/crear-schedule,                200",
            "ADMIN,    PUT,    /schedule-service/actualizar-schedule/ID,        200",
            "ADMIN,    GET,    /subscription-barbershop-service/actual-subscription/ID,  200",
            "ADMIN,    GET,    /subscription-barbershop-service/subscription-history/ID, 200",
            "ADMIN,    PUT,    /subscription-barbershop-service/renew,          200",
            "ADMIN,    POST,   /subscription-barbershop-service/create,         200",
            "ADMIN,    GET,    /reservation-service/barber/ID,                  200",
            // Una ruta sin regla queda bloqueada
            "ADMIN,    GET,    /ruta-que-no-existe,                             403",
    })
    void roleRules(String role, String method, String path, int expectedStatus) {
        WebTestClient.RequestBodySpec request = client.method(HttpMethod.valueOf(method))
                .uri(path.replace("ID", ID));
        if (!"NONE".equals(role)) {
            request.header("X-Test-Role", role);
        }
        request.exchange().expectStatus().isEqualTo(expectedStatus);
    }
}
