package com.smartbarber.infrastructure.drivenadapter.postgres.adapter;

import com.smartbarber.domain.port.UserPort;
import com.smartbarber.domain.model.user.User;
import com.smartbarber.infrastructure.drivenadapter.postgres.data.UserData;
import com.smartbarber.infrastructure.drivenadapter.postgres.mapper.UserAdapterMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;


@Component
@AllArgsConstructor
@Slf4j
public class UserAdapter implements UserPort {
    private final UserData userData;
    private final UserAdapterMapper mapper;
    @Override
    public Mono<User> findById(UUID id) {
        return userData.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public Mono<User> save(User user) {
        return userData.save(mapper.toEntity(user))
                .doOnNext(e -> log.info("Data registrada {}", e.toString()))
                .doOnSuccess(entityGuardada ->
                        log.info("Proceso de guardado finalizado correctamente"))
                .doOnError(error->
                        log.error("Error guardando barberia", error))
                .map(mapper::toDomain);
    }

    @Override
    public Mono<User> update(UUID id, User user) {
        return userData.save(mapper.toEntity(user))
                .map(mapper::toDomain);
    }

    @Override
    public Mono<Void> eliminarUsuario(UUID id) { return userData.deleteById(id); }

    @Override
    public Mono<Boolean> existByFirebaseId(String firebaseId){
        return userData.findByFirebaseId(firebaseId)
                .map(mapper::toDomain).hasElement();
    }

    @Override
    public Mono<User> findByFirebaseId(String firebaseId) {
        //System.out.println("UserAdapter.findByFirebaseId: " + firebaseId);
        return userData.findByFirebaseId(firebaseId)
                .doOnNext(e -> log.info("Data encontrada {}", e.toString()))
                .switchIfEmpty(Mono.error(new RuntimeException("User not found with firebaseId: " + firebaseId)))
        .map(mapper::toDomain);
    }

}
