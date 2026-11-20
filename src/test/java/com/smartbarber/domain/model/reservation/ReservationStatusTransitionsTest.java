package com.smartbarber.domain.model.reservation;

import com.smartbarber.domain.enums.ReservationStatus;
import com.smartbarber.domain.enums.ReservationType;
import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.reservation.ReservationMessageExceptions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
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

    private static final Duration NOTICE = Duration.ofMinutes(60);

    @Test
    void reprogramaConservandoLaDuracionYVuelveAPendiente() {
        Reservation moved = scheduled(ReservationStatus.CONFIRMED, NOW.plusDays(1))
                .reprogramar(NOW.plusDays(2), NOW, NOTICE);
        assertEquals(ReservationStatus.PENDING, moved.getStatus());
        assertEquals(NOW.plusDays(2), moved.getStartTime());
        assertEquals(NOW.plusDays(2).plusMinutes(30), moved.getEndTime());
    }

    @Test
    void noReprogramaSinLaAnticipacionMinima() {
        BusinessExceptions error = assertThrows(BusinessExceptions.class,
                () -> scheduled(ReservationStatus.CONFIRMED, NOW.plusMinutes(20))
                        .reprogramar(NOW.plusDays(1), NOW, NOTICE));
        assertEquals(ReservationMessageExceptions.TOO_LATE_TO_CHANGE, error.getExceptionMessage());
    }

    @Test
    void noReprogramaAUnaHoraDemasiadoCercana() {
        BusinessExceptions error = assertThrows(BusinessExceptions.class,
                () -> scheduled(ReservationStatus.PENDING, NOW.plusDays(1))
                        .reprogramar(NOW.plusMinutes(10), NOW, NOTICE));
        assertEquals(ReservationMessageExceptions.INVALID_NEW_DATE, error.getExceptionMessage());
    }

    @Test
    void detectaCrucesDeHorario() {
        Reservation booked = scheduled(ReservationStatus.CONFIRMED, NOW.plusDays(1));
        org.junit.jupiter.api.Assertions.assertTrue(
                booked.overlaps(NOW.plusDays(1).plusMinutes(15), NOW.plusDays(1).plusMinutes(45)));
        org.junit.jupiter.api.Assertions.assertFalse(
                booked.overlaps(NOW.plusDays(1).plusMinutes(30), NOW.plusDays(1).plusMinutes(60)));
    }
}
