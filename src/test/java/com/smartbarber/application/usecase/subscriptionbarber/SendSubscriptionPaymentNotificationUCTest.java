package com.smartbarber.application.usecase.subscriptionbarber;

import com.smartbarber.domain.enums.SubscriptionBarberStatus;
import com.smartbarber.domain.model.employee.Employee;
import com.smartbarber.domain.model.subscriptionbarber.SubscriptionBarbershop;
import com.smartbarber.domain.model.suscription.Subscription;
import com.smartbarber.domain.port.EmployeePort;
import com.smartbarber.domain.port.subscription.SubscriptionRepositoryPort;
import com.smartbarber.domain.port.subscriptionbarbershop.SubscriptionBarbershopRepositoryPort;
import com.smartbarber.infrastructure.drivenadapter.email.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigInteger;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SendSubscriptionPaymentNotificationUCTest {

    private static final UUID BARBERSHOP = UUID.randomUUID();
    private static final String ADMIN_EMAIL = "admin@barberia.test";

    private SubscriptionBarbershopRepositoryPort subscriptionPort;
    private EmailService emailService;
    private SendSubscriptionPaymentNotificationUC useCase;

    @BeforeEach
    void setUp() {
        subscriptionPort = mock(SubscriptionBarbershopRepositoryPort.class);
        SubscriptionRepositoryPort planPort = mock(SubscriptionRepositoryPort.class);
        EmployeePort employeePort = mock(EmployeePort.class);
        emailService = mock(EmailService.class);

        Subscription plan = mock(Subscription.class);
        when(plan.getName()).thenReturn("Plan Pro");
        when(planPort.findById(any())).thenReturn(Mono.just(plan));

        Employee admin = mock(Employee.class);
        when(admin.getEmail()).thenReturn(ADMIN_EMAIL);
        when(admin.getName()).thenReturn("Ana");
        when(employeePort.findAdministratorByBarbershopId(BARBERSHOP)).thenReturn(Mono.just(admin));
        when(emailService.sendEmail(anyString(), anyString(), anyString())).thenReturn(Mono.empty());

        useCase = new SendSubscriptionPaymentNotificationUC(subscriptionPort, planPort, employeePort, emailService);
    }

    @Test
    void avisaLaAdquisicionCuandoElPrimerPagoQuedaActivo() {
        SubscriptionBarbershop active = subscription(UUID.randomUUID(), SubscriptionBarberStatus.ACTIVE);
        when(subscriptionPort.findByBarberId(BARBERSHOP)).thenReturn(Flux.just(active));

        StepVerifier.create(useCase.notify(SubscriptionBarberStatus.PENDING, active)).verifyComplete();

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmail(eq(ADMIN_EMAIL), subject.capture(), body.capture());
        assertTrue(subject.getValue().contains("está activa"));
        assertTrue(body.getValue().contains("Plan Pro"));
        assertTrue(body.getValue().contains("Vigencia"));
    }

    @Test
    void avisaLaRenovacionAprobadaConLaFechaDeInicio() {
        SubscriptionBarbershop current = subscription(UUID.randomUUID(), SubscriptionBarberStatus.ACTIVE);
        SubscriptionBarbershop renewal = subscription(UUID.randomUUID(), SubscriptionBarberStatus.APPROVED);
        when(subscriptionPort.findByBarberId(BARBERSHOP)).thenReturn(Flux.just(current, renewal));

        StepVerifier.create(useCase.notify(SubscriptionBarberStatus.PENDING, renewal)).verifyComplete();

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmail(eq(ADMIN_EMAIL), subject.capture(), anyString());
        assertTrue(subject.getValue().contains("Renovación"));
    }

    @Test
    void avisaElPagoRechazado() {
        SubscriptionBarbershop declined = subscription(UUID.randomUUID(), SubscriptionBarberStatus.DECLINED);
        when(subscriptionPort.findByBarberId(BARBERSHOP)).thenReturn(Flux.just(declined));

        StepVerifier.create(useCase.notify(SubscriptionBarberStatus.PENDING, declined)).verifyComplete();

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmail(eq(ADMIN_EMAIL), anyString(), body.capture());
        assertTrue(body.getValue().contains("no se activó"));
    }

    @Test
    void noDuplicaElAvisoSiLaPasarelaReenviaElResultado() {
        SubscriptionBarbershop active = subscription(UUID.randomUUID(), SubscriptionBarberStatus.ACTIVE);

        StepVerifier.create(useCase.notify(SubscriptionBarberStatus.ACTIVE, active)).verifyComplete();

        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    void unFalloDelCorreoNoInterrumpeElPago() {
        SubscriptionBarbershop active = subscription(UUID.randomUUID(), SubscriptionBarberStatus.ACTIVE);
        when(subscriptionPort.findByBarberId(BARBERSHOP)).thenReturn(Flux.just(active));
        when(emailService.sendEmail(anyString(), anyString(), anyString()))
                .thenReturn(Mono.error(new IllegalStateException("Brevo caído")));

        StepVerifier.create(useCase.notify(SubscriptionBarberStatus.PENDING, active)).verifyComplete();
    }

    private static SubscriptionBarbershop subscription(UUID id, SubscriptionBarberStatus status) {
        Instant now = Instant.now();
        return SubscriptionBarbershop.rebuild(id, BARBERSHOP, 1, status, 30,
                BigInteger.valueOf(50000), (byte) 0, true, now, now,
                now, now.plus(30, ChronoUnit.DAYS));
    }
}
