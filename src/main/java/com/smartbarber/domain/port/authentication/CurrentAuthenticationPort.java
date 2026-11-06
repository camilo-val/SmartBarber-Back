package com.smartbarber.domain.port.authentication;

import com.smartbarber.domain.model.authentication.Authentication;
import reactor.core.publisher.Mono;

public interface CurrentAuthenticationPort {

    Mono<Authentication> getCurrentAuthentication();

}