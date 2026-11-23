package com.smartbarber.infrastructure.drivenadapter.postgres.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Table(name = "politica_penalizacion")
public class PenaltyPolicyEntity {

    @Id
    @Column("id_politica_penalizacion")
    private UUID id;
    @Column("id_barberia")
    private UUID barbershopId;
    @Column("penalizacion_activa")
    private Boolean enabled;
    @Column("tolerancia_minutos")
    private Integer toleranceMinutes;
    @Column("umbral_inasistencias")
    private Integer noShowThreshold;
    @Column("periodo_dias")
    private Integer countingPeriodDays;
    @Column("duracion_restriccion_dias")
    private Integer restrictionDays;
    @Column("anticipacion_cancelar_horas")
    private Integer cancelNoticeHours;
    @Column("permite_reprogramar")
    private Boolean allowReschedule;
    @Column("anticipacion_reprogramar_horas")
    private Integer rescheduleNoticeHours;
    @Column("fecha_modificacion")
    private Instant updatedAt;
    @Column("modificado_por")
    private UUID updatedBy;
}
