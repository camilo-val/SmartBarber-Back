-- =====================================================================
-- Política de penalización de clientes (RF-22, RF-82 a RF-85, RN-43 a RN-47)
--
-- OBLIGATORIO antes de desplegar el back que incluye /penalty-service.
-- Es idempotente: se puede ejecutar más de una vez sin efectos extra.
-- En una base nueva no hace falta: schema.sql ya incluye estas tablas.
-- =====================================================================

BEGIN;

-- Parámetros por barbería. Sin fila, el back usa los valores por defecto
-- (3 inasistencias en 30 días -> 15 días sin reservar, tolerancia 15 min,
-- cancelar y reprogramar hasta 24 h antes).
CREATE TABLE IF NOT EXISTS politica_penalizacion (
    id_politica_penalizacion       UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    id_barberia                    UUID        NOT NULL REFERENCES barberia (id_barberia) ON DELETE CASCADE,
    penalizacion_activa            BOOLEAN     NOT NULL DEFAULT TRUE,
    tolerancia_minutos             INTEGER     NOT NULL DEFAULT 15 CHECK (tolerancia_minutos BETWEEN 5 AND 30),
    umbral_inasistencias           INTEGER     NOT NULL DEFAULT 3  CHECK (umbral_inasistencias BETWEEN 2 AND 5),
    periodo_dias                   INTEGER     NOT NULL DEFAULT 30 CHECK (periodo_dias BETWEEN 15 AND 90),
    duracion_restriccion_dias      INTEGER     NOT NULL DEFAULT 15 CHECK (duracion_restriccion_dias BETWEEN 1 AND 30),
    anticipacion_cancelar_horas    INTEGER     NOT NULL DEFAULT 24 CHECK (anticipacion_cancelar_horas BETWEEN 1 AND 72),
    permite_reprogramar            BOOLEAN     NOT NULL DEFAULT TRUE,
    anticipacion_reprogramar_horas INTEGER     NOT NULL DEFAULT 24 CHECK (anticipacion_reprogramar_horas BETWEEN 1 AND 72),
    fecha_modificacion             TIMESTAMPTZ,
    modificado_por                 UUID        REFERENCES usuario (user_id) ON DELETE SET NULL,

    CONSTRAINT uq_politica_penalizacion_barberia UNIQUE (id_barberia)
);

-- Restricciones para crear reservas (no cobran dinero).
CREATE TABLE IF NOT EXISTS restriccion_cliente (
    id_restriccion_cliente UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    id_cliente             UUID         NOT NULL REFERENCES cliente (id_cliente) ON DELETE CASCADE,
    id_barberia            UUID         NOT NULL REFERENCES barberia (id_barberia) ON DELETE CASCADE,
    fecha_inicio           TIMESTAMPTZ  NOT NULL,
    fecha_fin              TIMESTAMPTZ  NOT NULL,
    estado                 VARCHAR(10)  NOT NULL CHECK (estado IN ('ACTIVE', 'EXPIRED', 'LIFTED')),
    inasistencias          INTEGER      NOT NULL DEFAULT 0,
    motivo_levantamiento   VARCHAR(200),
    levantada_por          UUID         REFERENCES usuario (user_id) ON DELETE SET NULL,
    fecha_levantamiento    TIMESTAMPTZ,

    CONSTRAINT ck_restriccion_cliente_fechas CHECK (fecha_inicio < fecha_fin),
    -- Levantar exige motivo (RN-46).
    CONSTRAINT ck_restriccion_cliente_levantamiento CHECK (
        estado <> 'LIFTED' OR char_length(trim(motivo_levantamiento)) BETWEEN 5 AND 200)
);

-- Una sola restricción ACTIVE por cliente y barbería.
CREATE UNIQUE INDEX IF NOT EXISTS uq_restriccion_cliente_activa
    ON restriccion_cliente (id_cliente, id_barberia)
    WHERE estado = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_restriccion_cliente_barberia_estado
    ON restriccion_cliente (id_barberia, estado);

CREATE INDEX IF NOT EXISTS idx_restriccion_cliente_estado_fin
    ON restriccion_cliente (estado, fecha_fin);

-- Inasistencias por cliente (el conteo filtra por cliente y estado NO_SHOW).
CREATE INDEX IF NOT EXISTS idx_reserva_cliente_estado
    ON reserva (id_cliente, estado);

COMMIT;
