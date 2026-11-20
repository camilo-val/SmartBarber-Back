package com.smartbarber.infrastructure.drivenadapter.postgres.entity;

import com.smartbarber.domain.enums.ServiceReservationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Builder
@AllArgsConstructor
@Getter
@NoArgsConstructor
@Table("reserva_servicio")
public class ServiceReservationEntity {
    @Column("id_reserva_servicio")
    private UUID id;
    @Column("id_reserva")
    private UUID reservationId;
    @Column("id_servicio")
    private Integer serviceId;
    @Column("estado")
    private ServiceReservationStatus status;
    @Column("duracion")
    private int duration;
    /** Precio cobrado al reservar (script db/precio_reserva_servicio.sql). */
    @Column("precio")
    private Integer price;
    @Column("fecha_creacion")
    private Instant createdAt;
    @Column("fecha_modificacion")
    private Instant updatedAt;

}
