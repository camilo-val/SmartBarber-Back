package com.smartbarber.infrastructure.entrypoint.reactiveweb.handler;

import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.error.ErrorRsDto;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.exception.TechnicalExceptions;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.exception.TechnicalMessageExceptions;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;

import static com.smartbarber.infrastructure.entrypoint.utils.constants.HandlerConstant.*;

@Component
@Order(-2)
@RequiredArgsConstructor
public class GlobalHandlerError implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        HttpStatus status = getStatus(ex);
        Map<String,ErrorRsDto> error = errorMap(ex);
        exchange.getResponse().setStatusCode(status);

        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return Mono.fromCallable(() -> objectMapper.writeValueAsBytes(error))
                .flatMap(bytes -> {
                    DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
                    return exchange.getResponse().writeWith(Mono.just(buffer));
                });
    }

    private HttpStatus getStatus(Throwable ex) {
        if (ex instanceof BusinessExceptions) {
            return HttpStatus.CONFLICT;
        } else if (ex instanceof TechnicalExceptions technicalExceptions) {
            return switch (technicalExceptions.getMensajesExcepciones()) {
                case BAD_REQUEST ->
                        HttpStatus.BAD_REQUEST;
                case INTERNAL_SERVER_ERROR, UNEXPECTED_ERROR ->
                        HttpStatus.INTERNAL_SERVER_ERROR;
                default ->
                        HttpStatus.SERVICE_UNAVAILABLE;
            };
        } else if (ex instanceof ConstraintViolationException) {
            return HttpStatus.BAD_REQUEST;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private Map<String, ErrorRsDto> errorMap(Throwable ex) {

        if (ex instanceof BusinessExceptions businessExceptions) {
            return buildMapError(businessExceptions.getExceptionMessage().getCode()
                    ,businessExceptions.getExceptionMessage().getMessage(),
                    BUSINESS
            );

        } else if (ex instanceof TechnicalExceptions technicalExceptions) {
            return buildMapError(technicalExceptions.getMensajesExcepciones().getCode()
                    , technicalExceptions.getMensajesExcepciones().getMessage(),
                    TECHNICAL
            );

        }else if (ex instanceof ConstraintViolationException constraintViolationException) {
            return buildMapError(TechnicalMessageExceptions.UNEXPECTED_ERROR.getCode()
                    , constraintViolationException.getMessage(),
                    TECHNICAL
            );
        }
        return buildMapError(TechnicalMessageExceptions.UNEXPECTED_ERROR.getCode()
                , ex.getMessage(),
                UNEXPECTED);


    }
    private Map<String, ErrorRsDto> buildMapError(String code, String message, String reason) {
        ErrorRsDto error = ErrorRsDto.builder()
                .reason(reason)
                .message(message)
                .code(code)
                .date(Instant.now())
                .build();

        return Map.of("data", error);
    }

}
