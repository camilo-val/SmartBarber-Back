package com.smartbarber.domain.model.schedule;

import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.schedule.ScheduleMessageExceptions;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** La jornada empieza antes de terminar y el almuerzo queda dentro de ella. */
class ScheduleRangesTest {

    private static final UUID EMPLOYEE = UUID.randomUUID();

    private static Schedule create(String start, String end, String lunchStart, String lunchEnd) {
        return Schedule.createSchedule(null, EMPLOYEE, LocalTime.parse(start), LocalTime.parse(end),
                LocalTime.parse(lunchStart), LocalTime.parse(lunchEnd), false);
    }

    @Test
    void aceptaUnAlmuerzoDentroDeLaJornada() {
        assertDoesNotThrow(() -> create("08:00", "18:00", "12:00", "13:00"));
    }

    @Test
    void rechazaUnAlmuerzoFueraDeLaJornada() {
        BusinessExceptions error = assertThrows(BusinessExceptions.class,
                () -> create("08:00", "18:00", "19:00", "20:00"));
        assertEquals(ScheduleMessageExceptions.LUNCH_OUTSIDE_WORKDAY, error.getExceptionMessage());
    }

    @Test
    void rechazaUnaJornadaInvertida() {
        BusinessExceptions error = assertThrows(BusinessExceptions.class,
                () -> create("18:00", "08:00", "12:00", "13:00"));
        assertEquals(ScheduleMessageExceptions.INVALID_WORKDAY_RANGE, error.getExceptionMessage());
    }

    @Test
    void reconstruirDesdeLaBaseNoAplicaLasReglasDeRango() {
        // Horarios guardados antes de la validación deben seguir leyéndose.
        assertDoesNotThrow(() -> Schedule.rebuild(1, EMPLOYEE, LocalTime.parse("08:00"), LocalTime.parse("18:00"),
                LocalTime.parse("19:00"), LocalTime.parse("20:00"), false));
    }
}
