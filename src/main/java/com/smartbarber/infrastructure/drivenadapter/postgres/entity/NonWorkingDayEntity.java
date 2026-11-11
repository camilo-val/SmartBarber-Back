package com.smartbarber.infrastructure.drivenadapter.postgres.entity;

import lombok.Data;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@ToString
@Table(name = "dia_no_laborable")
public class NonWorkingDayEntity {

    @Id
    @Column("id_dia_no_laborable")
    private UUID id;
    @Column("id_barberia")
    private UUID barbershopId;
    @Column("fecha")
    private LocalDate date;
    @Column("motivo")
    private String reason;
    @Column("creado_por")
    private UUID createdBy;
    @Column("fecha_creacion")
    private Instant createdAt;
}
