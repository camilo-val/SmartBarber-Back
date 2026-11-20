package com.smartbarber.infrastructure.entrypoint.reactiveweb.handler;

import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.error.ErrorRsDto;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.exception.TechnicalExceptions;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.exception.TechnicalMessageExceptions;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;

import static com.smartbarber.infrastructure.entrypoint.utils.constants.HandlerConstant.*;

/**
 * Respuesta única de errores de la API.
 *
 * <p>Nunca se envía al cliente el texto de una excepción inesperada (puede
 * contener consultas SQL, rutas internas o datos de otros registros): ese
 * detalle solo se registra en el log del servidor y la respuesta lleva un
 * mensaje genérico. Las validaciones de campos sí se devuelven, porque sus
 * mensajes los redacta el propio proyecto en los DTO.</p>
 */
@Slf4j
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
        } else if (ex instanceof ConstraintViolationException || isMalformedRequest(ex)) {
            return HttpStatus.BAD_REQUEST;
        } else if (ex instanceof ResponseStatusException responseStatus) {
            HttpStatus resolved = HttpStatus.resolve(responseStatus.getStatusCode().value());
            return resolved != null ? resolved : HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    /** Cuerpo ilegible, UUID o enumeración inválidos en la ruta o en el JSON. */
    private static boolean isMalformedRequest(Throwable ex) {
        return ex instanceof ServerWebInputException || ex instanceof IllegalArgumentException;
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

        } else if (ex instanceof ConstraintViolationException constraintViolationException) {
            // "campo: mensaje, campo: mensaje": el front lo muestra bajo cada campo.
            return buildMapError(TechnicalMessageExceptions.UNEXPECTED_ERROR.getCode()
                    , constraintViolationException.getMessage(),
                    TECHNICAL
            );
        } else if (isMalformedRequest(ex)) {
            log.warn("Solicitud mal formada: {}", ex.getMessage());
            return buildMapError(TechnicalMessageExceptions.BAD_REQUEST.getCode()
                    , TechnicalMessageExceptions.BAD_REQUEST.getMessage(),
                    TECHNICAL);
        } else if (ex instanceof ResponseStatusException responseStatus) {
            return buildMapError(TechnicalMessageExceptions.UNEXPECTED_ERROR.getCode()
                    , responseStatus.getStatusCode().toString(),
                    TECHNICAL);
        }

        // Error no controlado: el detalle técnico queda solo en el log.
        log.error("Error no controlado", ex);
        return buildMapError(TechnicalMessageExceptions.UNEXPECTED_ERROR.getCode()
                , TechnicalMessageExceptions.UNEXPECTED_ERROR.getMessage(),
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
