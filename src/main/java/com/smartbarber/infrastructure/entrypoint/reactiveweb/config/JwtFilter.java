package com.smartbarber.infrastructure.entrypoint.reactiveweb.config;

import com.smartbarber.application.usecase.athentication.AuthenticationUC;
import com.smartbarber.domain.enums.RoleType;
import com.smartbarber.domain.model.authentication.AuthenticatedPrincipal;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.exception.TechnicalExceptions;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.exception.TechnicalMessageExceptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
public class JwtFilter implements WebFilter {

    private final AuthenticationUC validateTokenUseCase;

    public JwtFilter(AuthenticationUC validateTokenUseCase) {
        this.validateTokenUseCase = validateTokenUseCase;
    }

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            WebFilterChain chain
    ) {

        String path = exchange.getRequest()
                .getPath()
                .value();

        log.info("========== REQUEST RECIBIDA ==========");
        log.info("[REQUEST] Método: {}", exchange.getRequest().getMethod());
        log.info("[REQUEST] Ruta: {}", path);
        log.info("[REQUEST] Upgrade: {}",
                exchange.getRequest().getHeaders().getUpgrade());
        log.info("=======================================");

        log.info("[JWT] Ruta solicitada: {}", path);
        log.info(
                "[JWT] Authorization presente: {}",
                exchange.getRequest()
                        .getHeaders()
                        .getFirst(HttpHeaders.AUTHORIZATION) != null
        );
        log.info(
                "[JWT] Parámetro token presente: {}",
                exchange.getRequest()
                        .getQueryParams()
                        .getFirst("token") != null
        );

        log.info(
                "[JWT] Parámetro token presente: {}",
                exchange.getRequest()
                        .getQueryParams()
                        .containsKey("token")
        );

        // Las rutas públicas no necesitan JWT
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        String authorization = exchange.getRequest()
                .getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);

// El navegador no permite configurar Authorization
// directamente al usar la API WebSocket.
        if ((authorization == null || authorization.isBlank())
                && (path.equals("/ws/virtual-queue")
                || path.equals("/ws/notifications")
                || path.equals("/ws/payment"))) {

            String token = exchange.getRequest()
                    .getQueryParams()
                    .getFirst("token");

            if (token != null && !token.isBlank()) {
                authorization = "Bearer " + token;
            }
        }

        // Sin token, Spring Security decidirá si permite el acceso
        if (authorization == null
                || !authorization.startsWith("Bearer ")
                || authorization.length() <= 7) {
            return chain.filter(exchange);
        }

        /*return validateTokenUseCase.authorizationSession(authorization)

                // Solo los errores de validación se convierten en 401
                .onErrorResume(TechnicalExceptions.class, error -> {
                    log.error(error.getMessage(), error);
                    return Mono.error(() -> new TechnicalExceptions(TechnicalMessageExceptions.UNAUTHORIZED));
                })

                .flatMap(token -> {


                    // Lista de roles válidos para la aplicación
                    if (!List.of(
                            RoleType.CUSTOMER,
                            RoleType.BARBER,
                            RoleType.ADMIN
                    ).contains(token)) {
                        return Mono.error(() -> new TechnicalExceptions(TechnicalMessageExceptions.FORBIDDEN));


                    }

                    if (path.equals("/ws/notifications")
                            && token != RoleType.CUSTOMER) {
                        return Mono.error(
                                () -> new TechnicalExceptions(
                                        TechnicalMessageExceptions.FORBIDDEN
                                )
                        );
                    }

                    Authentication authentication =
                            new UsernamePasswordAuthenticationToken(
                                    token,
                                    null,
                                    List.of(
                                            new SimpleGrantedAuthority(
                                                    "ROLE_" + token
                                            )
                                    )
                            );

                    return chain.filter(exchange)
                            .contextWrite(
                                    ReactiveSecurityContextHolder
                                            .withAuthentication(authentication)
                            );
                });*/
        return validateTokenUseCase.authorizationSession(authorization)

                .flatMap(authData -> {

                    String role = switch (authData.getUser().getRoleId()) {
                        case 1 -> "ADMIN";
                        case 2 -> "BARBER";
                        case 3 -> "CUSTOMER";
                        default -> throw new TechnicalExceptions(
                                TechnicalMessageExceptions.FORBIDDEN
                        );
                    };

                    AuthenticatedPrincipal principal =
                            new AuthenticatedPrincipal(authData);

                    Authentication authentication =
                            new UsernamePasswordAuthenticationToken(
                                    principal,
                                    null,
                                    List.of(
                                            new SimpleGrantedAuthority(
                                                    "ROLE_" + role
                                            )
                                    )
                            );

                    return chain.filter(exchange)
                            .contextWrite(
                                    ReactiveSecurityContextHolder
                                            .withAuthentication(authentication)
                            );
                });
    }

    private boolean isPublicPath(String path) {
        return path.startsWith("/auth/")
                || path.equals("/auth")
                /*|| path.startsWith("/reservation-service/")
                || path.equals("/reservation-service")
                || path.startsWith("/schedule-service/")
                || path.equals("/schedule-service")
                || path.startsWith("/public/")
                || path.equals("/public")*/;
    }
}