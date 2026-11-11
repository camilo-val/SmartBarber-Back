package com.smartbarber.infrastructure.entrypoint.reactiveweb.handler.employee;

import com.smartbarber.application.usecase.athentication.AuthenticationUC;
import com.smartbarber.application.usecase.employee.BlockEmployeeUC;
import com.smartbarber.application.usecase.employee.CreateEmployeeUC;
import com.smartbarber.application.usecase.employee.SearchEmployeeUC;
import com.smartbarber.application.usecase.employee.UpdateEmployeeUC;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.employee.EmployeeOnboardingRqDto;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.employee.EmployeeRqDto;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.exception.TechnicalExceptions;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.exception.TechnicalMessageExceptions;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.mapper.employee.EmployeeEntryMapper;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.mapper.employee.EmployeeOnboardingMapper;
import com.smartbarber.infrastructure.entrypoint.utils.validateRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static com.smartbarber.infrastructure.entrypoint.utils.constants.HandlerConstant.IDTOKEN;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

@Component
@AllArgsConstructor
@Slf4j
public class EmployeeHandler {
    private final EmployeeEntryMapper mapper;
    private final EmployeeOnboardingMapper onboardingMapper;
    private final CreateEmployeeUC createEmployeeUC;
    private final BlockEmployeeUC blockEmployeeUC;
    private final validateRequest validateRequest;
    private final UpdateEmployeeUC updateEmployeeUC;
    private final SearchEmployeeUC searchEmployeeUC;
    private final AuthenticationUC authenticationUc;

    public Mono<ServerResponse> createEmployee(ServerRequest request){
        return request.bodyToMono(EmployeeRqDto.class)
                .doOnNext(validateRequest::validate)
                .map(mapper::toDomain)
                .flatMap(createEmployeeUC::createEmployee)
                .map(mapper::toResponse)
                .flatMap(response -> ServerResponse.created(request.uri()).bodyValue(response))
                .switchIfEmpty(Mono.error(new TechnicalExceptions(TechnicalMessageExceptions.BAD_REQUEST)));
    }

    public Mono<ServerResponse> onBoarding(ServerRequest request){
        return request.bodyToMono(EmployeeOnboardingRqDto.class)
                .doOnNext(validateRequest::validate)
                .flatMap(requestBody ->
                        Mono.justOrEmpty(request.headers().firstHeader(IDTOKEN))
                                .switchIfEmpty(Mono.error(() -> new TechnicalExceptions(TechnicalMessageExceptions.BAD_REQUEST)))
                                .flatMap(authenticationUc::getUserUid)
                                .map(uid -> onboardingMapper.toDomain(requestBody, uid)))
                .flatMap(createEmployeeUC::createEmployeeOnboarding)
                .doOnNext(e -> log.info("Employee created with id: {}", e))
                .map(onboardingMapper::toResponse)
                .flatMap(response -> ServerResponse.created(request.uri()).bodyValue(response))
                .switchIfEmpty(Mono.error(new TechnicalExceptions(TechnicalMessageExceptions.BAD_REQUEST)));
    }

    public Mono<ServerResponse> findEmployeeByName(ServerRequest request){
        return searchEmployeeUC.buscarPorNombre(request.pathVariable("nombre"))
                .map(mapper::toResponse)
                .flatMap(response -> ServerResponse.ok().bodyValue(response))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> findByEmployeeByDocument(ServerRequest request){
        return searchEmployeeUC.buscarPorDocuemnto(request.pathVariable("documento"))
                .map(mapper::toResponse)
                .flatMap( response -> ServerResponse.ok().bodyValue(response))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> findEmployeeById(ServerRequest request){
        return searchEmployeeUC.buscarPorId(request.pathVariable("id"))
                .map(mapper::toResponse)
                .flatMap( response -> ServerResponse.ok().bodyValue(response))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> updateEmployee(ServerRequest request){
        return request.bodyToMono(EmployeeRqDto.class)
                .doOnNext(validateRequest::validate)
                .map(mapper::toDomain)
                .flatMap(employee -> updateEmployeeUC
                        .employeeUpdate(request.pathVariable("id"), employee))
                .map(mapper::toResponse)
                .flatMap(response -> ServerResponse.accepted().bodyValue(response));
    }
    public Mono<ServerResponse> blockEmployee(ServerRequest request){
        return blockEmployeeUC.blockEmployee(UUID.fromString(request.pathVariable("id")))
                .then(ServerResponse.accepted().build());
    }

    public Mono<ServerResponse> findEmployeeByBarbershop(ServerRequest request){
        return searchEmployeeUC.findByBarberId(UUID.fromString(request.pathVariable("barberId")))
                .map(mapper::toResponse)
                .collectList()
                .flatMap( response -> ServerResponse.ok().bodyValue(response))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

}
