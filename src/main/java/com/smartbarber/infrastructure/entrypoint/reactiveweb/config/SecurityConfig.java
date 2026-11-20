
package com.smartbarber.infrastructure.entrypoint.reactiveweb.config;

import com.smartbarber.domain.enums.RoleType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter filter;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                .authorizeExchange(exchange -> exchange

                        // =================================================
                        // RUTAS PUBLICAS
                        // =================================================

                        .pathMatchers(HttpMethod.POST,
                                "/auth/verify",
                                "/barber-service/onboarding",
                                "/customer-service/onboarding",
                                "/public/user-service/crear-usuario"
                        ).permitAll()

                        // Consultas públicas de barberías
                        .pathMatchers(HttpMethod.GET,
                                "/barber-service/name/**",
                                "/barber-service/company-name/**",
                                "/barber-service/id/**",
                                "/barber-service/email",
                                "/actuator/health"
                        ).permitAll()

                        // Consultas públicas de servicios
                        .pathMatchers(HttpMethod.GET,
                                "/services-service/name/**",
                                "/services-service/id/**"
                        ).permitAll()


                        // =================================================
                        // BARBERIA
                        // =================================================

                        .pathMatchers(HttpMethod.POST,
                                "/public/barber-service/create-barber"
                        ).hasRole(RoleType.ADMIN.name())

                        .pathMatchers(HttpMethod.GET,
                                "/barber-service/find-all"
                        ).hasAnyRole(
                                RoleType.CUSTOMER.name(),
                                RoleType.BARBER.name(),
                                RoleType.ADMIN.name()
                        )

                        .pathMatchers(HttpMethod.GET,
                                "/barber-service/document/**"
                        ).hasRole(RoleType.ADMIN.name())

                        .pathMatchers(HttpMethod.PUT,
                                "/barber-service/update-barber/**"
                        ).hasRole(RoleType.ADMIN.name())


                        // =================================================
                        // CLIENTES
                        // =================================================

                        .pathMatchers(HttpMethod.POST,
                                "/public/customer-service/create-customer"
                        ).hasRole(RoleType.ADMIN.name())

                        .pathMatchers(HttpMethod.GET,
                                "/customer-service/id/**"
                        ).hasAnyRole(
                                RoleType.CUSTOMER.name(),
                                RoleType.ADMIN.name()
                        )

                        .pathMatchers(HttpMethod.GET,
                                "/customer-service/name/**",
                                "/customer-service/document/**"
                        ).hasRole(RoleType.ADMIN.name())

                        .pathMatchers(HttpMethod.PUT,
                                "/customer-service/update-customer/**"
                        ).hasAnyRole(
                                RoleType.CUSTOMER.name(),
                                RoleType.ADMIN.name()
                        )

                        .pathMatchers(HttpMethod.PATCH,
                                "/customer-service/block-customer/**",
                                "/customer-service/unblock-customer/**"
                        ).hasAnyRole(
                                RoleType.CUSTOMER.name(),
                                RoleType.ADMIN.name()
                        )


                        // =================================================
                        // EMPLEADOS
                        // =================================================

                        .pathMatchers(HttpMethod.POST,
                                "/public/employee-service/crear-empleado",
                                "/public/employee-service/onboarding"
                        ).hasRole(RoleType.ADMIN.name())

                        // Barberos que el cliente puede reservar (sin datos personales).
                        .pathMatchers(HttpMethod.GET,
                                "/public/employee-service/barbershop/*/bookable"
                        ).hasAnyRole(
                                RoleType.CUSTOMER.name(),
                                RoleType.BARBER.name(),
                                RoleType.ADMIN.name()
                        )

                        // Listado completo del equipo (incluye documento, correo y
                        // celular): solo para el personal de la barbería.
                        .pathMatchers(HttpMethod.GET,
                                "/public/employee-service/barbershop/**"
                        ).hasAnyRole(
                                RoleType.BARBER.name(),
                                RoleType.ADMIN.name()
                        )

                                // Consultas de empleados
                                .pathMatchers(HttpMethod.GET,
                                        "/employee-service/nombre/**",
                                        "/employee-service/id/**"
                                ).hasAnyRole(
                                        RoleType.BARBER.name(),
                                        RoleType.ADMIN.name()
                                )

                        .pathMatchers(HttpMethod.GET,
                                "/employee-service/documento/**"
                        ).hasRole(RoleType.ADMIN.name())

                        .pathMatchers(HttpMethod.PUT,
                                "/employee-service/actualizar-empleado/**"
                        ).hasRole(RoleType.ADMIN.name())

                        // Acceso del equipo: el administrador bloquea o desbloquea barberos.
                        .pathMatchers(HttpMethod.GET,
                                "/employee-service/team"
                        ).hasRole(RoleType.ADMIN.name())

                        .pathMatchers(HttpMethod.PATCH,
                                "/employee-service/block-employee/**",
                                "/employee-service/unblock-employee/**"
                        ).hasRole(RoleType.ADMIN.name())


                        // =================================================
                        // SERVICIOS
                        // =================================================

                        .pathMatchers(HttpMethod.POST,
                                "/services-service/crear-servicio"
                        ).hasRole(RoleType.ADMIN.name())

                        .pathMatchers(HttpMethod.GET,
                                "/services-service/barberia/**"
                        ).hasAnyRole(
                                RoleType.CUSTOMER.name(),
                                RoleType.BARBER.name(),
                                RoleType.ADMIN.name()
                        )

                        .pathMatchers(HttpMethod.PUT,
                                "/services-service/actualizar-service/**"
                        ).hasRole(RoleType.ADMIN.name())


                        // =================================================
                        // HORARIOS
                        // =================================================

                        .pathMatchers(HttpMethod.POST,
                                "/schedule-service/crear-schedule"
                        ).hasRole(RoleType.ADMIN.name())

                        .pathMatchers(HttpMethod.GET,
                                "/schedule-service/id/**"
                        ).hasAnyRole(
                                RoleType.BARBER.name(),
                                RoleType.ADMIN.name()
                        )

                        .pathMatchers(HttpMethod.GET,
                                "/schedule-service/empleado/**"
                        ).hasAnyRole(
                                RoleType.CUSTOMER.name(),
                                RoleType.BARBER.name(),
                                RoleType.ADMIN.name()
                        )

                        .pathMatchers(HttpMethod.PUT,
                                "/schedule-service/actualizar-schedule/**"
                        ).hasRole(RoleType.ADMIN.name())


                        // =================================================
                        // RESERVAS
                        // =================================================

                        .pathMatchers(HttpMethod.POST,
                                "/reservation-service/create"
                        ).hasRole(RoleType.CUSTOMER.name())

                        .pathMatchers(HttpMethod.GET,
                                "/reservation-service/employee/**",
                                "/reservation-service/barber/**"
                        ).hasAnyRole(
                                RoleType.BARBER.name(),
                                RoleType.ADMIN.name(),
                                RoleType.CUSTOMER.name()
                        )

                        .pathMatchers(HttpMethod.GET,
                                "/reservation-service/customer/**"
                        ).hasAnyRole(
                                RoleType.CUSTOMER.name(),
                                RoleType.ADMIN.name()
                        )

                        .pathMatchers(HttpMethod.GET,
                                "/reservation-service/*"
                        ).hasAnyRole(
                                RoleType.CUSTOMER.name(),
                                RoleType.BARBER.name(),
                                RoleType.ADMIN.name()
                        )

                        .pathMatchers(HttpMethod.PATCH,
                                "/reservation-service/*/confirm",
                                "/reservation-service/*/complete",
                                "/reservation-service/*/no-show"
                        ).hasAnyRole(
                                RoleType.BARBER.name(),
                                RoleType.ADMIN.name()
                        )

                        .pathMatchers(HttpMethod.PATCH,
                                "/reservation-service/*/cancel"
                        ).hasAnyRole(
                                RoleType.CUSTOMER.name(),
                                RoleType.BARBER.name(),
                                RoleType.ADMIN.name()
                        )


                        // =================================================
                        // RESEÑAS DE RESERVAS
                        // =================================================

                        .pathMatchers(HttpMethod.POST,
                                "/reservation-review-service/crear-resena-reserva"
                        ).hasRole(RoleType.CUSTOMER.name())

                        .pathMatchers(HttpMethod.GET,
                                "/reservation-review-service/id/**",
                                "/reservation-review-service/reserva/**"
                        ).hasAnyRole(
                                RoleType.CUSTOMER.name(),
                                RoleType.BARBER.name(),
                                RoleType.ADMIN.name()
                        )

                        .pathMatchers(HttpMethod.PUT,
                                "/reservation-review-service/actualizar-resena-reserva/**"
                        ).hasAnyRole(
                                RoleType.CUSTOMER.name(),
                                RoleType.ADMIN.name()
                        )


                        // =================================================
                        // USUARIOS
                        // =================================================

                        .pathMatchers(HttpMethod.GET,
                                "/user-service/id/**"
                        ).hasRole(RoleType.ADMIN.name())

                        .pathMatchers(HttpMethod.PUT,
                                "/user-service/actualizar-usuario/**"
                        ).hasRole(RoleType.ADMIN.name())


                        // =================================================
                        // NOTIFICACIONES
                        // =================================================

                        .pathMatchers(HttpMethod.POST,
                                "/notification-service/crear-notificacion"
                        ).hasAnyRole(
                                RoleType.BARBER.name(),
                                RoleType.ADMIN.name()
                        )

                        .pathMatchers(HttpMethod.GET,
                                "/notification-service/id/**",
                                "/notification-service/reserva/**"
                        ).hasAnyRole(
                                RoleType.CUSTOMER.name(),
                                RoleType.BARBER.name(),
                                RoleType.ADMIN.name()
                        )

                        .pathMatchers(HttpMethod.GET,
                                "/notification-service/cliente/**"
                        ).hasAnyRole(
                                RoleType.CUSTOMER.name(),
                                RoleType.ADMIN.name()
                        )

                        .pathMatchers(HttpMethod.PUT,
                                "/notification-service/actualizar-notificacion/**"
                        ).hasAnyRole(
                                RoleType.BARBER.name(),
                                RoleType.ADMIN.name()
                        )


                        // =================================================
                        // DIAS NO LABORABLES
                        // =================================================

                        .pathMatchers(HttpMethod.GET,
                                "/non-working-day-service/barbershop/*"
                        ).hasAnyRole(
                                RoleType.CUSTOMER.name(),
                                RoleType.BARBER.name(),
                                RoleType.ADMIN.name()
                        )

                        .pathMatchers(HttpMethod.POST,
                                "/non-working-day-service"
                        ).hasRole(RoleType.ADMIN.name())

                        .pathMatchers(HttpMethod.DELETE,
                                "/non-working-day-service/*"
                        ).hasRole(RoleType.ADMIN.name())


                        // =================================================
                        // ESTADISTICAS
                        // =================================================

                        .pathMatchers(HttpMethod.GET,
                                "/statistics-service/**"
                        ).hasRole(RoleType.ADMIN.name())


                        // =================================================
                        // ROLES
                        // =================================================

                        .pathMatchers(
                                "/role-service/**"
                        ).hasRole(RoleType.ADMIN.name())


                        // =================================================
                        // SUSCRIPCIONES
                        // =================================================

                        .pathMatchers(
                                "/subscription-service/**"
                        ).hasRole(RoleType.ADMIN.name())

                        .pathMatchers(HttpMethod.POST,
                                "/subscription-barbershop-service/create"
                        ).hasRole(RoleType.ADMIN.name())

                        .pathMatchers(HttpMethod.PUT,
                                "/subscription-barbershop-service/renew"
                        ).hasRole(RoleType.ADMIN.name())

                        .pathMatchers(HttpMethod.GET,
                                "/subscription-barbershop-service/actual-subscription/**",
                                "/subscription-barbershop-service/subscription-history/**"
                        ).hasAnyRole(
                                RoleType.BARBER.name(),
                                RoleType.ADMIN.name()
                        )


                        // =================================================
                        // COLA VIRTUAL
                        // =================================================

                        .pathMatchers(HttpMethod.POST,
                                "/api/v1/queue/join"
                        ).hasRole(RoleType.CUSTOMER.name())

                        .pathMatchers(HttpMethod.POST,
                                "/api/v1/queue/*/open",
                                "/api/v1/queue/*/close",
                                "/api/v1/queue/*/next",
                                "/api/v1/queue/*/complete"
                        ).hasRole(RoleType.BARBER.name())

                        .pathMatchers(HttpMethod.GET,
                                "/api/v1/queue/**"
                        ).hasAnyRole(
                                RoleType.CUSTOMER.name(),
                                RoleType.BARBER.name(),
                                RoleType.ADMIN.name()
                        )


                        // =================================================
                        // WEBSOCKETS
                        // =================================================

                        .pathMatchers(
                                "/ws/payment",
                                "/ws/virtual-queue",
                                "/ws/notifications"
                        ).authenticated()


                        // =================================================
                        // BLOQUEAR CUALQUIER RUTA NO CONFIGURADA
                        // =================================================

                        .anyExchange().denyAll()
                )

                .addFilterAt(filter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("*"));
        configuration.setAllowedMethods(List.of("*"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
