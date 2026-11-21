package com.smartbarber.application.usecase.subscriptionbarber;

import com.smartbarber.domain.enums.SubscriptionBarberStatus;
import com.smartbarber.domain.model.subscriptionbarber.SubscriptionBarbershop;
import com.smartbarber.domain.port.EmployeePort;
import com.smartbarber.domain.port.subscription.SubscriptionRepositoryPort;
import com.smartbarber.domain.port.subscriptionbarbershop.SubscriptionBarbershopRepositoryPort;
import com.smartbarber.infrastructure.drivenadapter.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;

/**
 * Avisa por correo al administrador de la barbería el resultado del pago de su
 * suscripción, igual que el recordatorio de vencimiento de la renovación:
 *
 * <ul>
 *   <li><b>ACTIVE</b>: plan adquirido (o renovación que entra en vigencia de inmediato).</li>
 *   <li><b>APPROVED</b>: renovación pagada que se activará al vencer el plan actual.</li>
 *   <li><b>DECLINED</b>: el pago fue rechazado.</li>
 * </ul>
 *
 * Solo se notifica la transición desde PENDING, así un reenvío de la
 * pasarela no duplica el correo. Un fallo al enviar se registra y no afecta
 * el procesamiento del pago.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SendSubscriptionPaymentNotificationUC {

    private static final ZoneId ZONE_ID = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-CO"));
    private static final String DEFAULT_PLAN_NAME = "SmartBarber";

    private final SubscriptionBarbershopRepositoryPort subscriptionPort;
    private final SubscriptionRepositoryPort planPort;
    private final EmployeePort employeePort;
    private final EmailService emailService;

    public Mono<Void> notify(SubscriptionBarberStatus previousStatus, SubscriptionBarbershop updated) {
        if (updated == null || previousStatus != SubscriptionBarberStatus.PENDING
                || !isNotifiable(updated.getStatus())) {
            return Mono.empty();
        }

        return Mono.zip(planName(updated), isRenewal(updated), renewalStart(updated))
                .flatMap(data -> sendToAdministrator(updated,
                        buildMessage(updated, data.getT1(), data.getT2(), data.getT3())))
                .onErrorResume(error -> {
                    log.error("No fue posible enviar el aviso de pago de la suscripción {}",
                            updated.getId(), error);
                    return Mono.empty();
                });
    }

    private static boolean isNotifiable(SubscriptionBarberStatus status) {
        return status == SubscriptionBarberStatus.ACTIVE
                || status == SubscriptionBarberStatus.APPROVED
                || status == SubscriptionBarberStatus.DECLINED;
    }

    private Mono<String> planName(SubscriptionBarbershop subscription) {
        if (subscription.getSubscriptionId() == null) {
            return Mono.just(DEFAULT_PLAN_NAME);
        }
        return planPort.findById(subscription.getSubscriptionId())
                .map(plan -> plan.getName() == null || plan.getName().isBlank() ? DEFAULT_PLAN_NAME : plan.getName())
                .defaultIfEmpty(DEFAULT_PLAN_NAME);
    }

    /** Es renovación si la barbería ya tuvo otra suscripción pagada antes. */
    private Mono<Boolean> isRenewal(SubscriptionBarbershop subscription) {
        return subscriptionPort.findByBarberId(subscription.getBarberId())
                .filter(other -> !Objects.equals(other.getId(), subscription.getId()))
                .any(other -> other.getStatus() == SubscriptionBarberStatus.ACTIVE
                        || other.getStatus() == SubscriptionBarberStatus.EXPIRED
                        || other.getStatus() == SubscriptionBarberStatus.APPROVED);
    }

    /** Para una renovación APPROVED: fecha en que termina el plan activo (inicio de la nueva). */
    private Mono<Instant> renewalStart(SubscriptionBarbershop subscription) {
        if (subscription.getStatus() != SubscriptionBarberStatus.APPROVED) {
            return Mono.just(Instant.EPOCH);
        }
        return subscriptionPort.findByBarberId(subscription.getBarberId())
                .filter(other -> other.getStatus() == SubscriptionBarberStatus.ACTIVE
                        && other.getExpirationDate() != null)
                .map(SubscriptionBarbershop::getExpirationDate)
                .sort(Comparator.reverseOrder())
                .next()
                .defaultIfEmpty(Instant.EPOCH);
    }

    private Mono<Void> sendToAdministrator(SubscriptionBarbershop subscription, Message message) {
        return employeePort.findAdministratorByBarbershopId(subscription.getBarberId())
                .flatMap(admin -> {
                    if (admin.getEmail() == null || admin.getEmail().isBlank()) {
                        log.warn("El administrador de la barbería {} no tiene correo", subscription.getBarberId());
                        return Mono.empty();
                    }
                    return emailService.sendEmail(admin.getEmail(), message.subject(),
                                    message.html(admin.getName()))
                            .doOnSuccess(ignored -> log.info("Aviso de pago ({}) enviado para la suscripción {}",
                                    subscription.getStatus(), subscription.getId()));
                })
                .switchIfEmpty(Mono.fromRunnable(() ->
                        log.warn("Sin administrador para avisar el pago de la barbería {}", subscription.getBarberId())))
                .then();
    }

    Message buildMessage(SubscriptionBarbershop subscription, String planName, boolean renewal, Instant renewalStart) {
        String plan = escape(planName);
        return switch (subscription.getStatus()) {
            case ACTIVE -> new Message(
                    renewal ? "Tu suscripción de SmartBarber fue renovada"
                            : "¡Bienvenido! Tu suscripción de SmartBarber está activa",
                    (renewal
                            ? "Recibimos el pago de la renovación del plan <b>%s</b> y ya está vigente."
                            : "Recibimos el pago del plan <b>%s</b>. Tu barbería ya tiene acceso a todos los módulos de SmartBarber.")
                            .formatted(plan)
                            + validity(subscription.getStartDate(), subscription.getExpirationDate())
                            + "<p>Puedes revisar el detalle en <b>Historial de suscripción</b> dentro del panel.</p>");
            case APPROVED -> new Message(
                    "Renovación de tu suscripción de SmartBarber confirmada",
                    ("Recibimos el pago de la renovación del plan <b>%s</b>. "
                            + "Se activará automáticamente cuando termine tu plan actual").formatted(plan)
                            + (Instant.EPOCH.equals(renewalStart) ? "." : ", el <b>%s</b>.".formatted(format(renewalStart)))
                            + "<p>No necesitas hacer nada más.</p>");
            case DECLINED -> new Message(
                    "No pudimos procesar el pago de tu suscripción de SmartBarber",
                    ("El pago del plan <b>%s</b> fue rechazado, por lo que la suscripción no se "
                            + (renewal ? "renovó" : "activó") + ".").formatted(plan)
                            + "<p>Ingresa al panel, en <b>Suscripción</b>, para intentarlo de nuevo con otro medio de pago.</p>");
            default -> throw new IllegalStateException("Estado sin aviso: " + subscription.getStatus());
        };
    }

    private static String validity(Instant start, Instant end) {
        if (start == null || end == null) {
            return "";
        }
        return "<p>Vigencia: del <b>%s</b> al <b>%s</b>.</p>".formatted(format(start), format(end));
    }

    private static String format(Instant instant) {
        return DATE_FORMAT.format(instant.atZone(ZONE_ID));
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    record Message(String subject, String body) {
        String html(String adminName) {
            String greeting = adminName == null || adminName.isBlank() ? "Hola," : "Hola, " + escape(adminName) + ":";
            return "<p>" + greeting + "</p><div>" + body + "</div><p>Gracias por utilizar SmartBarber.<br>Equipo SmartBarber</p>";
        }
    }
}
