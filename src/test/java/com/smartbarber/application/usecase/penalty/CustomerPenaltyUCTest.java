package com.smartbarber.application.usecase.penalty;

import com.smartbarber.application.usecase.nonworkingday.BarbershopAdminResolver;
import com.smartbarber.domain.enums.ReservationStatus;
import com.smartbarber.domain.enums.ReservationType;
import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.penalty.PenaltyMessageExceptions;
import com.smartbarber.domain.model.customer.Customer;
import com.smartbarber.domain.model.penalty.CustomerRestriction;
import com.smartbarber.domain.model.penalty.PenaltyPolicy;
import com.smartbarber.domain.model.reservation.Reservation;
import com.smartbarber.domain.port.CustomerPort;
import com.smartbarber.domain.port.authentication.CurrentAuthenticationPort;
import com.smartbarber.domain.port.penalty.CustomerRestrictionPort;
import com.smartbarber.domain.port.reservation.ReservationRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerPenaltyUCTest {

    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final UUID SHOP = UUID.randomUUID();

    private ReservationRepositoryPort reservationPort;
    private CustomerRestrictionPort restrictionPort;
    private final List<CustomerRestriction> stored = new ArrayList<>();
    private CustomerPenaltyUC useCase;

    @BeforeEach
    void setUp() {
        reservationPort = mock(ReservationRepositoryPort.class);
        restrictionPort = mock(CustomerRestrictionPort.class);
        CustomerPort customerPort = mock(CustomerPort.class);
        PenaltyPolicyUC policyUC = mock(PenaltyPolicyUC.class);
        PenaltyNotifier notifier = mock(PenaltyNotifier.class);

        when(policyUC.forBarbershop(any())).thenReturn(Mono.just(PenaltyPolicy.defaults(SHOP)));
        when(notifier.barbershopName(any())).thenReturn(Mono.just("The Palace"));
        Customer customer = mock(Customer.class);
        when(customer.getName()).thenReturn("Ana");
        when(customerPort.findById(CUSTOMER)).thenReturn(Mono.just(customer));

        when(restrictionPort.findByCustomerAndBarbershop(CUSTOMER, SHOP))
                .thenAnswer(invocation -> Flux.fromIterable(new ArrayList<>(stored)));
        when(restrictionPort.save(any())).thenAnswer(invocation -> {
            CustomerRestriction restriction = invocation.getArgument(0);
            stored.add(restriction);
            return Mono.just(restriction);
        });

        useCase = new CustomerPenaltyUC(reservationPort, restrictionPort, customerPort, policyUC, notifier,
                mock(BarbershopAdminResolver.class), mock(CurrentAuthenticationPort.class));
    }

    private static Reservation noShow(int daysAgo) {
        OffsetDateTime start = OffsetDateTime.now().minusDays(daysAgo);
        return Reservation.rebuild(UUID.randomUUID(), SHOP, CUSTOMER, UUID.randomUUID(),
                ReservationType.SERVICE_RESERVATION, ReservationStatus.NO_SHOW, 30, start, start.plusMinutes(30),
                "", start.minusDays(1), start.plusMinutes(20), null);
    }

    @Test
    void laTerceraInasistenciaCreaLaRestriccionYBloqueaLaReserva() {
        Reservation third = noShow(0);
        when(reservationPort.findByCustomerId(CUSTOMER)).thenReturn(Flux.just(noShow(12), noShow(5), third));

        StepVerifier.create(useCase.registerNoShow(third))
                .assertNext(outcome -> {
                    assertTrue(outcome.restrictionCreated());
                    assertTrue(outcome.view().standing().isRestricted());
                    assertEquals("The Palace", outcome.view().barbershopName());
                })
                .verifyComplete();

        ArgumentCaptor<CustomerRestriction> saved = ArgumentCaptor.forClass(CustomerRestriction.class);
        verify(restrictionPort).save(saved.capture());
        assertEquals(3, saved.getValue().getNoShowCount());

        StepVerifier.create(useCase.ensureCanBook(CUSTOMER, SHOP))
                .expectErrorSatisfies(error -> assertEquals(PenaltyMessageExceptions.CUSTOMER_RESTRICTED,
                        ((BusinessExceptions) error).getExceptionMessage()))
                .verify();
    }

    @Test
    void laSegundaInasistenciaSoloAdvierte() {
        Reservation second = noShow(0);
        when(reservationPort.findByCustomerId(CUSTOMER)).thenReturn(Flux.just(noShow(8), second));

        StepVerifier.create(useCase.registerNoShow(second))
                .assertNext(outcome -> {
                    assertFalse(outcome.restrictionCreated());
                    assertEquals(2, outcome.view().standing().noShowCount());
                })
                .verifyComplete();

        verify(restrictionPort, never()).save(any());
        StepVerifier.create(useCase.ensureCanBook(CUSTOMER, SHOP)).verifyComplete();
    }
}
