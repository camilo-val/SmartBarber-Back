package com.smartbarber.domain.port;

import com.smartbarber.domain.model.user.AccessChange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.UUID;

/** Historial de bloqueos y desbloqueos de cuentas. */
public interface AccessChangePort {

    Mono<AccessChange> save(AccessChange change);

    /** El cambio más reciente de cada usuario (los usuarios sin cambios se omiten). */
    Flux<AccessChange> findLatestByUserIds(Collection<UUID> userIds);
}
