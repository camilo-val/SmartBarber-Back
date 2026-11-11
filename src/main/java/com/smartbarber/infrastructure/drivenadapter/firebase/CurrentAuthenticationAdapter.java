package com.smartbarber.infrastructure.drivenadapter.firebase;


import com.smartbarber.domain.model.authentication.AuthenticatedPrincipal;
import com.smartbarber.domain.model.authentication.Authentication;
import com.smartbarber.domain.port.authentication.CurrentAuthenticationPort;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Component
public class CurrentAuthenticationAdapter
        implements CurrentAuthenticationPort {

    @Override
    public Mono<Authentication> getCurrentAuthentication() {

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(authentication ->
                        (AuthenticatedPrincipal)
                                Objects.requireNonNull(authentication.getPrincipal())
                )
                .map(AuthenticatedPrincipal::data);
    }
}