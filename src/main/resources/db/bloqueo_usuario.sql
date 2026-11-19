-- =====================================================================
-- Historial de bloqueos y desbloqueos de cuentas (RF-16, RN-48)
-- Ejecutar una sola vez en la base de datos ANTES de desplegar el backend
-- que incluye GET /employee-service/team y el motivo de bloqueo.
-- Es idempotente (IF NOT EXISTS) y no afecta al backend que ya está en
-- producción: solo agrega una tabla nueva.
-- =====================================================================

CREATE TABLE IF NOT EXISTS bloqueo_usuario (
    id_bloqueo_usuario UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID         NOT NULL REFERENCES usuario (user_id) ON DELETE CASCADE,
    accion             VARCHAR(10)  NOT NULL CHECK (accion IN ('BLOCK', 'UNBLOCK')),
    motivo             VARCHAR(200),
    -- Administrador que hizo el cambio; se conserva el registro si su cuenta se elimina.
    realizado_por      UUID         REFERENCES usuario (user_id) ON DELETE SET NULL,
    fecha              TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- Todo bloqueo registra su motivo (RN-48); en el desbloqueo es opcional.
    CONSTRAINT ck_bloqueo_usuario_motivo
        CHECK (accion <> 'BLOCK' OR char_length(trim(motivo)) BETWEEN 5 AND 200)
);

-- Último cambio de cada usuario (listado del equipo): DISTINCT ON (user_id) ... ORDER BY fecha DESC.
CREATE INDEX IF NOT EXISTS idx_bloqueo_usuario_user_fecha
    ON bloqueo_usuario (user_id, fecha DESC);

-- Verificación
-- 1) La columna usuario.estado debe aceptar 'BLOCKED' y 'ACTIVE' (texto o enum con esos valores):
--    SELECT column_name, data_type, udt_name FROM information_schema.columns
--     WHERE table_name = 'usuario' AND column_name = 'estado';
-- 2) Historial:
--    SELECT * FROM bloqueo_usuario ORDER BY fecha DESC;
