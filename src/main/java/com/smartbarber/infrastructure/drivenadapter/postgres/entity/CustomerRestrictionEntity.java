package com.smartbarber.infrastructure.drivenadapter.postgres.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Table(name = "restriccion_cliente")
public class CustomerRestrictionEntity {

    @Id
    @Column("id_restriccion_cliente")
    private UUID id;
    @Column("id_cliente")
    private UUID customerId;
    @Column("id_barberia")
    private UUID barbershopId;
    @Column("fecha_inicio")
    private Instant startsAt;
    @Column("fecha_fin")
    private Instant endsAt;
    @Column("estado")
    private String status;
    @Column("inasistencias")
    private Integer noShowCount;
    @Column("motivo_levantamiento")
    private String liftReason;
    @Column("levantada_por")
    private UUID liftedBy;
    @Column("fecha_levantamiento")
    private Instant liftedAt;
}
