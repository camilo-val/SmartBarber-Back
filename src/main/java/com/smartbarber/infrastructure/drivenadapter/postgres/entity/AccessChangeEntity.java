package com.smartbarber.infrastructure.drivenadapter.postgres.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "bloqueo_usuario")
public class AccessChangeEntity {

    @Id
    @Column("id_bloqueo_usuario")
    private UUID id;
    @Column("user_id")
    private UUID userId;
    @Column("accion")
    private String action;
    @Column("motivo")
    private String reason;
    @Column("realizado_por")
    private UUID performedBy;
    @Column("fecha")
    private Instant createdAt;
}
