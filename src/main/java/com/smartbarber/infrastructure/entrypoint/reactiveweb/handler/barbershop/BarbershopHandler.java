package com.smartbarber.infrastructure.entrypoint.reactiveweb.handler.barbershop;

import com.smartbarber.application.usecase.athentication.AuthenticationUC;
import com.smartbarber.application.usecase.barbershop.BarbershopUpdateUC;
import com.smartbarber.application.usecase.barbershop.FindBarbershopUC;
import com.smartbarber.application.usecase.barbershop.CreateBarbershopUC;
import com.smartbarber.application.usecase.barbershop.OnboardingBarberShopUc;
import com.smartbarber.infrastructure.drivenadapter.email.EmailService;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.barbershop.BarbershopOnBoardingRqDto;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.barbershop.BarbershopRqDto;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.exception.TechnicalExceptions;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.exception.TechnicalMessageExceptions;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.mapper.barbershop.BarberOnboardingEntryMapper;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.mapper.barbershop.BarbershopEntryMapper;
import com.smartbarber.infrastructure.entrypoint.utils.validateRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

@Component
@AllArgsConstructor
@Slf4j
public class BarbershopHandler {
    private final BarbershopEntryMapper mapper;
    private final BarberOnboardingEntryMapper barberOnboardingMapper;
    private final CreateBarbershopUC createBarbershopUC;
    private final validateRequest validateRequest;
    private final FindBarbershopUC findBarbershopUC;
    private final BarbershopUpdateUC barbershopUpdateUC;
    private final OnboardingBarberShopUc onboardingBarberShopUc;
    private final AuthenticationUC authenticationUc;
    private final EmailService mailSender;

    public Mono<ServerResponse> createBarbershop(ServerRequest request){
        return request.bodyToMono(BarbershopRqDto.class)
                .doOnNext(validateRequest::validate)
                .map(mapper::toDomain)
                .flatMap(createBarbershopUC::createBarbershop)
                .map(mapper::toResponse)
                .flatMap(response -> ServerResponse.created(request.uri()).bodyValue(response))
                .switchIfEmpty(Mono.error(new TechnicalExceptions(TechnicalMessageExceptions.BAD_REQUEST)) );
    }

    public Mono<ServerResponse> onBoardingBarber(ServerRequest request){
        return request.bodyToMono(BarbershopOnBoardingRqDto.class)
                .doOnNext(validateRequest::validate)
                .flatMap(body
                        -> authenticationUc.getUserUid(request.headers().header(AUTHORIZATION).getFirst())
                .map(uid -> barberOnboardingMapper.toCommand(body, uid)))
                .flatMap(onboardingBarberShopUc::execute)
                .doOnNext(response -> log.info("onBoardingBarberUc.execute: 1111{}", response))
                .map(barberOnboardingMapper::toResponse)
                .flatMap(response -> ServerResponse.created(request.uri()).bodyValue(response))
                .switchIfEmpty(Mono.error(new TechnicalExceptions(TechnicalMessageExceptions.BAD_REQUEST)) );
    }


    public Mono<ServerResponse> findBarbershopByName(ServerRequest request){
        return findBarbershopUC.findByName(request.pathVariable("name"))
                .map(mapper::toResponse)
                .flatMap(response -> ServerResponse.ok().bodyValue(response))
                .switchIfEmpty(ServerResponse.notFound().build());

    }

    public Mono<ServerResponse> findAllBarberShop(ServerRequest request){
        return findBarbershopUC.findAll()
                .map(mapper::toResponse)
                .collectList()
                .flatMap(response -> ServerResponse.ok().bodyValue(response));

    }

    public Mono<ServerResponse> findBarbershopByCompanyName(ServerRequest request){
        return findBarbershopUC.findByCompanyName(request.pathVariable("companyName"))
                .map(mapper::toResponse)
                .collectList()
                .flatMap(response -> ServerResponse.ok().bodyValue(response));
    }

    public Mono<ServerResponse> findByBarbershopByDocument(ServerRequest request){
        return findBarbershopUC.findByDocument(request.pathVariable("document"))
                .map(mapper::toResponse)
                .flatMap(response -> ServerResponse.ok().bodyValue(response))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> findBarbershopById(ServerRequest request){
        return findBarbershopUC.findById(request.pathVariable("id"))
                .map(mapper::toResponse)
                .flatMap(response -> ServerResponse.ok().bodyValue(response))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> updateBarbershop(ServerRequest request){
        return request.bodyToMono(BarbershopRqDto.class)
                .doOnNext(validateRequest::validate)
                .map(mapper::toDomain)
                .flatMap(barberia -> barbershopUpdateUC
                        .barberUpdate(request.pathVariable("id"), barberia))
                .map(mapper::toResponse)
                .flatMap(response -> ServerResponse.accepted().bodyValue(response));
    }

    public Mono<ServerResponse> test(ServerRequest request) {

        return mailSender.sendEmail(
                        request.pathVariable("email"),
                        "Prueba SMTP",
                        "Correo enviado desde Render"
                )
                .then(ServerResponse.ok().bodyValue("OK"))
                // El detalle técnico va al log; al cliente solo un resultado genérico.
                .onErrorResume(ex -> {
                    log.error("Falló la prueba de envío de correo", ex);
                    return ServerResponse.status(502).bodyValue("SMTP ERROR");
                });
    }
}
