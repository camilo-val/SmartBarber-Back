package com.smartbarber.infrastructure.entrypoint.reactiveweb.config;

import com.smartbarber.application.usecase.athentication.AuthenticationUC;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class JwtFilterOnboardingTest {

    private final AuthenticationUC authenticationUC = mock(AuthenticationUC.class);
    private final JwtFilter filter = new JwtFilter(authenticationUC);

    private boolean passes(MockServerHttpRequest request) {
        AtomicBoolean reached = new AtomicBoolean(false);
        WebFilterChain chain = exchange -> {
            reached.set(true);
            return Mono.empty();
        };
        try {
            filter.filter(MockServerWebExchange.from(request), chain).block();
        } catch (RuntimeException ignored) {
            // la validación del token falló
        }
        return reached.get();
    }

    @Test
    void barbershopAndCustomerOnboardingSkipSessionValidation() {
        // La cuenta de Firebase todavía no existe en SmartBarber.
        when(authenticationUC.authorizationSession(anyString())).thenReturn(Mono.error(new RuntimeException("no registrado")));

        assertTrue(passes(MockServerHttpRequest.post("/barber-service/onboarding").header(HttpHeaders.AUTHORIZATION, "Bearer nuevo").build()));
        assertTrue(passes(MockServerHttpRequest.post("/customer-service/onboarding").header(HttpHeaders.AUTHORIZATION, "Bearer nuevo").build()));
        verify(authenticationUC, never()).authorizationSession(anyString());
    }

    @Test
    void otherRoutesStillRequireRegisteredUser() {
        when(authenticationUC.authorizationSession(anyString())).thenReturn(Mono.error(new RuntimeException("no registrado")));

        assertFalse(passes(MockServerHttpRequest.post("/non-working-day-service").header(HttpHeaders.AUTHORIZATION, "Bearer nuevo").build()));
        assertFalse(passes(MockServerHttpRequest.get("/barber-service/onboarding").header(HttpHeaders.AUTHORIZATION, "Bearer nuevo").build()));
        verify(authenticationUC, times(2)).authorizationSession(anyString());
    }
}
