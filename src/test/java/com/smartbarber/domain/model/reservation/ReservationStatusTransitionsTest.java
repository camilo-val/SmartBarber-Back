package com.smartbarber.domain.model.reservation;

import com.smartbarber.domain.enums.ReservationStatus;
import com.smartbarber.domain.enums.ReservationType;
import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.reservation.ReservationMessageExceptions;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Reglas para finalizar una reserva o registrar la inasistencia del cliente. */
class ReservationStatusTransitionsTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-20T15:00:00-05:00");

    private static Reservation scheduled(ReservationStatus status, OffsetDateTime start) {
        return Reservation.rebuild(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                ReservationType.SERVICE_RESERVATION, status, 30, start, start.plusMinutes(30),
                "", start.minusDays(1), null, null);
    }

    @Test
    void finalizaUnaReservaConfirmadaQueYaEmpezo() {
        Reservation done = scheduled(ReservationStatus.CONFIRMED, NOW.minusMinutes(40)).finalizar(NOW);
        assertEquals(ReservationStatus.COMPLETED, done.getStatus());
    }

    @Test
    void noFinalizaUnaReservaPendiente() {
        BusinessExceptions error = assertThrows(BusinessExceptions.class,
                () -> scheduled(ReservationStatus.PENDING, NOW.minusMinutes(40)).finalizar(NOW));
        assertEquals(ReservationMessageExceptions.CANNOT_COMPLETE_RESERVATION, error.getExceptionMessage());
    }

    @Test
    void noFinalizaAntesDeLaHoraDeInicio() {
        BusinessExceptions error = assertThrows(BusinessExceptions.class,
                () -> scheduled(ReservationStatus.CONFIRMED, NOW.plusHours(2)).finalizar(NOW));
        assertEquals(ReservationMessageExceptions.RESERVATION_NOT_STARTED, error.getExceptionMessage());
    }

    @Test
    void registraInasistenciaDeUnaReservaConfirmadaVencida() {
        Reservation noShow = scheduled(ReservationStatus.CONFIRMED, NOW.minusMinutes(10)).registrarInasistencia(NOW);
        assertEquals(ReservationStatus.NO_SHOW, noShow.getStatus());
    }

    @Test
    void noRegistraInasistenciaDeUnaReservaCancelada() {
        BusinessExceptions error = assertThrows(BusinessExceptions.class,
                () -> scheduled(ReservationStatus.CANCELLED, NOW.minusMinutes(10)).registrarInasistencia(NOW));
        assertEquals(ReservationMessageExceptions.CANNOT_MARK_NO_SHOW, error.getExceptionMessage());
    }
}
