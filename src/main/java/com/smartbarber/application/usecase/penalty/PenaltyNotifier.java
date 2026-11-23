package com.smartbarber.application.usecase.penalty;

import com.smartbarber.domain.model.barbershop.Barbershop;
import com.smartbarber.domain.model.penalty.CustomerRestriction;
import com.smartbarber.domain.port.BarberShopRepositoryPort;
import com.smartbarber.domain.port.CustomerPort;
import com.smartbarber.infrastructure.drivenadapter.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * Avisos al cliente sobre su restricción. Un fallo al enviar el correo se
 * registra y nunca interrumpe la operación que lo originó.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PenaltyNotifier {

    static final ZoneId ZONE = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-CO"));
    private static final String DEFAULT_SHOP = "la barbería";

    private final CustomerPort customerPort;
    private final BarberShopRepositoryPort barbershopPort;
    private final EmailService emailService;

    /** Nombre de la barbería para los mensajes; genérico si no se encuentra. */
    public Mono<String> barbershopName(UUID barbershopId) {
        if (barbershopId == null) {
            return Mono.just(DEFAULT_SHOP);
        }
        return barbershopPort.findById(barbershopId)
                .map(Barbershop::getName)
                .filter(name -> name != null && !name.isBlank())
                .defaultIfEmpty(DEFAULT_SHOP)
                .onErrorReturn(DEFAULT_SHOP);
    }

    /** "Ya puedes volver a reservar": al vencer o al levantarse la restricción. */
    public Mono<Void> restrictionEnded(CustomerRestriction restriction) {
        return Mono.zip(customerPort.findById(restriction.getCustomerId()), barbershopName(restriction.getBarbershopId()))
                .flatMap(data -> {
                    if (data.getT1().getEmail() == null || data.getT1().getEmail().isBlank()) {
                        return Mono.<Void>empty();
                    }
                    String shop = escape(data.getT2());
                    String body = "<p>Hola, " + escape(data.getT1().getName()) + ":</p>"
                            + "<p>Ya puedes volver a reservar en <b>" + shop + "</b>.</p>"
                            + "<p>Recuerda que si no puedes asistir a una cita, cancelarla a tiempo "
                            + "no cuenta como inasistencia.</p>"
                            + "<p>Gracias por utilizar SmartBarber.</p>";
                    return emailService.sendEmail(data.getT1().getEmail(),
                            "Ya puedes volver a reservar en " + data.getT2(), body);
                })
                .onErrorResume(error -> {
                    log.error("No fue posible avisar el fin de la restricción {}", restriction.getId(), error);
                    return Mono.empty();
                })
                .then();
    }

    public static String formatDate(Instant instant) {
        return instant == null ? "" : DATE.format(instant.atZone(ZONE));
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
