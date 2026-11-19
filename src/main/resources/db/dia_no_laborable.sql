-- =====================================================================
-- Días no laborables y festivos por barbería
-- Ejecutar una sola vez en la base de datos ANTES de desplegar el backend
-- que incluye /non-working-day-service. Es idempotente (IF NOT EXISTS).
-- =====================================================================

CREATE TABLE IF NOT EXISTS dia_no_laborable (
    id_dia_no_laborable UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_barberia         UUID         NOT NULL REFERENCES barberia (id_barberia) ON DELETE CASCADE,
    fecha               DATE         NOT NULL,
    motivo              VARCHAR(80)  NOT NULL CHECK (char_length(trim(motivo)) BETWEEN 3 AND 80),
    creado_por          UUID,
    fecha_creacion      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- Una barbería no puede registrar dos veces la misma fecha. El índice que
    -- crea esta restricción (id_barberia, fecha) también acelera las consultas
    -- por rango de fechas y la validación de reservas.
    CONSTRAINT uq_dia_no_laborable_barberia_fecha UNIQUE (id_barberia, fecha)
);

-- Verificación
-- SELECT * FROM dia_no_laborable ORDER BY id_barberia, fecha;
