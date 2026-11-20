package com.smartbarber.domain.model.schedule;

import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.schedule.ScheduleMessageExceptions;

import java.time.LocalTime;
import java.util.UUID;

public class Schedule {

    private final Integer id;
    private final UUID empleadoId;
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final LocalTime lunchStartTime;
    private final LocalTime lunchEndTime;
    private final Boolean vacation;

    private Schedule(
            Integer id,
            UUID empleadoId,
            LocalTime startTime,
            LocalTime endTime,
            LocalTime lunchStartTime,
            LocalTime lunchEndTime,
            Boolean vacation
    ) {
        this.id = id;
        this.empleadoId = empleadoId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.lunchStartTime = lunchStartTime;
        this.lunchEndTime = lunchEndTime;
        this.vacation = vacation;
    }

    public static Schedule createSchedule(
            Integer id,
            UUID empleadoId,
            LocalTime startTime,
            LocalTime endTime,
            LocalTime lunchStartTime,
            LocalTime lunchEndTime,
            Boolean vacation
    ) {

        validateInputs(
                startTime,
                endTime,
                lunchStartTime,
                lunchEndTime,
                vacation
        );

        validateRanges(startTime, endTime, lunchStartTime, lunchEndTime, vacation);

        return new Schedule(
                null,
                empleadoId,
                startTime,
                endTime,
                lunchStartTime,
                lunchEndTime,
                vacation
        );
    }

    public static Schedule update(
            Integer id,
            UUID empleadoId,
            LocalTime startTime,
            LocalTime endTime,
            LocalTime lunchStartTime,
            LocalTime lunchEndTime,
            Boolean vacation
    ) {

        validateInputs(
                startTime,
                endTime,
                lunchStartTime,
                lunchEndTime,
                vacation
        );

        validateRanges(startTime, endTime, lunchStartTime, lunchEndTime, vacation);

        return new Schedule(
                id,
                empleadoId,
                startTime,
                endTime,
                lunchStartTime,
                lunchEndTime,
                vacation
        );
    }

    public static Schedule rebuild(
            Integer id,
            UUID empleadoId,
            LocalTime startTime,
            LocalTime endTime,
            LocalTime lunchStartTime,
            LocalTime lunchEndTime,
            Boolean vacation
    ) {

        if (id == null) {
            throw new BusinessExceptions(
                    ScheduleMessageExceptions.INVALID_DATA
            );
        }

        validateInputs(
                startTime,
                endTime,
                lunchStartTime,
                lunchEndTime,
                vacation
        );

        return new Schedule(
                id,
                empleadoId,
                startTime,
                endTime,
                lunchStartTime,
                lunchEndTime,
                vacation
        );
    }

    private static void validateInputs(
            LocalTime startTime,
            LocalTime endTime,
            LocalTime lunchStartTime,
            LocalTime lunchEndTime,
            Boolean vacation
    ) {

        if (vacation == null) {
            throw new BusinessExceptions(
                    ScheduleMessageExceptions.INVALID_DATA
            );
        }

        // En vacaciones el horario se conserva (para cuando regrese) o se omite:
        // no se exige que venga vacío. Si viene, debe estar completo.
        if (Boolean.TRUE.equals(vacation)) {
            boolean anyTime = startTime != null || endTime != null
                    || lunchStartTime != null || lunchEndTime != null;
            boolean allTimes = startTime != null && endTime != null
                    && lunchStartTime != null && lunchEndTime != null;
            if (anyTime && !allTimes) {
                throw new BusinessExceptions(
                        ScheduleMessageExceptions.INVALID_DATA
                );
            }
            return;
        }

        boolean missingSchedule = startTime == null
                || endTime == null
                || lunchStartTime == null
                || lunchEndTime == null;

        if (missingSchedule) {
            throw new BusinessExceptions(
                    ScheduleMessageExceptions.INVALID_DATA
            );
        }
    }

    /**
     * Reglas de rango para los horarios que se crean o modifican. No se aplica
     * al reconstruir desde la base de datos, para no romper la lectura de
     * horarios guardados antes de existir esta validación.
     */
    private static void validateRanges(
            LocalTime startTime,
            LocalTime endTime,
            LocalTime lunchStartTime,
            LocalTime lunchEndTime,
            Boolean vacation
    ) {
        if (Boolean.TRUE.equals(vacation)) {
            return;
        }

        // La jornada debe empezar antes de terminar.
        if (!startTime.isBefore(endTime)) {
            throw new BusinessExceptions(
                    ScheduleMessageExceptions.INVALID_WORKDAY_RANGE
            );
        }

        // El almuerzo empieza antes de terminar y queda dentro de la jornada.
        boolean lunchInsideWorkday = lunchStartTime.isBefore(lunchEndTime)
                && !lunchStartTime.isBefore(startTime)
                && !lunchEndTime.isAfter(endTime);

        if (!lunchInsideWorkday) {
            throw new BusinessExceptions(
                    ScheduleMessageExceptions.LUNCH_OUTSIDE_WORKDAY
            );
        }
    }

    public Integer getId() {
        return id;
    }

    public UUID getEmpleadoId() {
        return empleadoId;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public LocalTime getLunchStartTime() {
        return lunchStartTime;
    }

    public LocalTime getLunchEndTime() {
        return lunchEndTime;
    }

    public Boolean getVacation() {
        return vacation;
    }
}