package com.smartbarber.infrastructure.entrypoint.reactiveweb.handler.customer;

import com.smartbarber.application.usecase.athentication.AuthenticationUC;
import com.smartbarber.application.usecase.customer.BlockCustomerUC;
import com.smartbarber.application.usecase.customer.UpdateCustomerUC;
import com.smartbarber.application.usecase.customer.CreateCustomerUC;
import com.smartbarber.application.usecase.customer.SearchCustomerUC;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.customer.CustomerOnboardingRqDto;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.customer.CustomerRqDto;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.exception.TechnicalExceptions;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.exception.TechnicalMessageExceptions;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.mapper.customer.CustomerEntryMapper;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.mapper.customer.CustomerOnboardingEntryMapper;
import com.smartbarber.infrastructure.entrypoint.utils.constants.HandlerConstant;
import com.smartbarber.infrastructure.entrypoint.utils.validateRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@AllArgsConstructor
@Slf4j
public class CustomerHandler {
    private final CustomerEntryMapper mapper;
    private final CreateCustomerUC createCustomerUC;
    private final validateRequest validateRequest;
    private final UpdateCustomerUC updateCustomerUC;
    private final SearchCustomerUC searchCustomerUC;
    private final AuthenticationUC authenticationUc;
    private final CustomerOnboardingEntryMapper  customerOnboardingEntryMapper;
    private final BlockCustomerUC blockCustomerUC;

    public Mono<ServerResponse> createCustomer(ServerRequest request){
        return request.bodyToMono(CustomerRqDto.class)
                .doOnNext(validateRequest::validate)
                .map(mapper::toDomain)
                .flatMap(createCustomerUC::createCustomer)
                .map(mapper::toResponse)
                .flatMap( response -> ServerResponse.created(request.uri()).bodyValue(response))
                .switchIfEmpty(Mono.error(new TechnicalExceptions(TechnicalMessageExceptions.BAD_REQUEST)));
    }

    public Mono<ServerResponse> onBoardCustomer(ServerRequest request){
        return request.bodyToMono(CustomerOnboardingRqDto.class)
                .doOnNext(validateRequest::validate)
                .flatMap(requestBody -> authenticationUc.getUserUid(request.headers().firstHeader(HandlerConstant.IDTOKEN))
                        .map(uid -> customerOnboardingEntryMapper.toCommand(requestBody,uid))
                .flatMap(createCustomerUC::onBoardingClient)
                .map(customerOnboardingEntryMapper::toResponse))
                .flatMap( response -> ServerResponse.created(request.uri()).bodyValue(response))
                .switchIfEmpty(Mono.error(new TechnicalExceptions(TechnicalMessageExceptions.BAD_REQUEST)));
    }

    public Mono<ServerResponse> findCustomerByName(ServerRequest request){
        return searchCustomerUC.findByName(request.pathVariable("name"))
                .map(mapper::toResponse)
                .flatMap( response -> ServerResponse.ok().bodyValue(response))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> findByCustomerByDocument(ServerRequest request){
        return searchCustomerUC.findByDocument(request.pathVariable("document"))
                .map(mapper::toResponse)
                .flatMap( response -> ServerResponse.ok().bodyValue(response))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> findCustomerById(ServerRequest request){
        return searchCustomerUC.findById(request.pathVariable("id"))
                .map(mapper::toResponse)
                .flatMap( response -> ServerResponse.ok().bodyValue(response))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> updateCustomer(ServerRequest request){
        return request.bodyToMono(CustomerRqDto.class)
                .doOnNext(validateRequest::validate)
                .map(mapper::toDomain)
                .flatMap( client -> updateCustomerUC
                        .customerUpdate(request.pathVariable("id"), client))
                .map(mapper::toResponse)
                .flatMap( response -> ServerResponse.accepted().bodyValue(response));
    }
    public Mono<ServerResponse> blockCustomer(ServerRequest request){
        return blockCustomerUC.blockCustomer(UUID.fromString(request.pathVariable("id")))
                .then(ServerResponse.accepted().build());
    }
}
