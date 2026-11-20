package com.smartbarber.infrastructure.entrypoint.reactiveweb.handler.employee;

import com.smartbarber.application.usecase.athentication.AuthenticationUC;
import com.smartbarber.application.usecase.employee.CreateEmployeeUC;
import com.smartbarber.application.usecase.employee.EmployeeAccessUC;
import com.smartbarber.application.usecase.employee.FindBookableBarbersUC;
import com.smartbarber.application.usecase.employee.SearchEmployeeUC;
import com.smartbarber.application.usecase.employee.UpdateEmployeeUC;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.employee.EmployeeOnboardingRqDto;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.employee.EmployeeAccessRqDto;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.employee.BookableBarberRsDto;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.employee.EmployeeRqDto;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.employee.TeamMemberRsDto;
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
    private final EmployeeAccessUC employeeAccessUC;
    private final validateRequest validateRequest;
    private final UpdateEmployeeUC updateEmployeeUC;
    private final SearchEmployeeUC searchEmployeeUC;
        private final AuthenticationUC authenticationUc;
    private final FindBookableBarbersUC findBookableBarbersUC;

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
        /**
     * GET /public/employee-service/barbershop/{barberId}/bookable — barberos que
     * el cliente puede reservar (cuenta activa y rol BARBER), sin datos personales.
     */
    public Mono<ServerResponse> findBookableBarbers(ServerRequest request) {
        return findBookableBarbersUC.findByBarbershop(UUID.fromString(request.pathVariable("barberId")))
                .map(BookableBarberRsDto::from)
                .collectList()
                .flatMap(barbers -> ServerResponse.ok().bodyValue(barbers));
    }

    /** GET /employee-service/team — equipo de la barbería del administrador, con el estado de cada cuenta. */
    public Mono<ServerResponse> findTeam(ServerRequest request) {
        return employeeAccessUC.findTeam()
                .map(TeamMemberRsDto::from)
                .collectList()
                .flatMap(team -> ServerResponse.ok().bodyValue(team));
    }

    /** PATCH /employee-service/block-employee/{id}  { "reason": "..." } — devuelve el barbero con su nuevo estado. */
    public Mono<ServerResponse> blockEmployee(ServerRequest request) {
        return Mono.zip(employeeIdOf(request), accessBodyOf(request))
                .flatMap(input -> employeeAccessUC.block(input.getT1(), input.getT2().reason()))
                .map(TeamMemberRsDto::from)
                .flatMap(member -> ServerResponse.ok().bodyValue(member));
    }

    /** PATCH /employee-service/unblock-employee/{id}  { "reason": "..." } (opcional) — devuelve el barbero con su nuevo estado. */
    public Mono<ServerResponse> unblockEmployee(ServerRequest request) {
        return Mono.zip(employeeIdOf(request), accessBodyOf(request))
                .flatMap(input -> employeeAccessUC.unblock(input.getT1(), input.getT2().reason()))
                .map(TeamMemberRsDto::from)
                .flatMap(member -> ServerResponse.ok().bodyValue(member));
    }

    /** El cuerpo es opcional: sin cuerpo, el motivo queda vacío. */
    private Mono<EmployeeAccessRqDto> accessBodyOf(ServerRequest request) {
        return request.bodyToMono(EmployeeAccessRqDto.class)
                .doOnNext(validateRequest::validate)
                .defaultIfEmpty(EmployeeAccessRqDto.EMPTY);
    }

    private Mono<UUID> employeeIdOf(ServerRequest request) {
        return Mono.fromCallable(() -> UUID.fromString(request.pathVariable("id")))
                .onErrorMap(IllegalArgumentException.class,
                        error -> new TechnicalExceptions(TechnicalMessageExceptions.BAD_REQUEST));
    }

    public Mono<ServerResponse> findEmployeeByBarbershop(ServerRequest request){
        return searchEmployeeUC.findByBarberId(UUID.fromString(request.pathVariable("barberId")))
                .map(mapper::toResponse)
                .collectList()
                .flatMap( response -> ServerResponse.ok().bodyValue(response))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

}
