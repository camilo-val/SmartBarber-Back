-- =====================================================================
-- SmartBarber - Esquema completo de la base de datos (PostgreSQL 13+)
--
-- Reconstruido desde el código del back (entidades R2DBC, consultas
-- @Query, StatisticsAdapter, enums y reglas de dominio), porque el acceso
-- a la base original venció. Crea todo lo que el back usa hoy, incluidos
-- los cambios de bloqueo_usuario.sql, dia_no_laborable.sql,
-- precio_reserva_servicio.sql, estadisticas_indices.sql y penalizacion_clientes.sql: en una base
-- nueva NO hace falta ejecutar esos scripts después de este.
--
-- Uso: ejecutar una sola vez sobre una base VACÍA. Es idempotente
-- (IF NOT EXISTS / ON CONFLICT), así que repetirlo no duplica nada.
--
-- Convenciones:
--   * Los id UUID los genera la base (gen_random_uuid()); el back inserta
--     con id nulo y lee el generado.
--   * Las fechas son TIMESTAMPTZ: el back trabaja con Instant /
--     OffsetDateTime (UTC) y la zona de negocio es America/Bogota.
--   * Los estados se guardan como texto con el nombre del enum de Java y
--     un CHECK con los valores válidos.
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;   -- gen_random_uuid() en PG < 13

SET search_path TO public;

-- ---------------------------------------------------------------------
-- 1. Seguridad: roles y usuarios (Firebase)
-- ---------------------------------------------------------------------

-- Ids fijos: el back los usa directamente (RoleType: ADMIN=1, BARBER=2, CUSTOMER=3).
CREATE TABLE IF NOT EXISTS rol (
    id_rol  SMALLINT    PRIMARY KEY,
    nombre  VARCHAR(30) NOT NULL UNIQUE,
    estado  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
);

CREATE TABLE IF NOT EXISTS usuario (
    user_id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    uid_firebase       VARCHAR(128) NOT NULL,
    estado             VARCHAR(20)  NOT NULL,
    id_rol             SMALLINT     NOT NULL,
    fecha_creacion     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    fecha_modificacion TIMESTAMPTZ,

    CONSTRAINT uq_usuario_uid_firebase UNIQUE (uid_firebase),
    CONSTRAINT fk_usuario_rol FOREIGN KEY (id_rol)
        REFERENCES rol (id_rol) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ck_usuario_estado CHECK (estado IN ('ACTIVE', 'BLOCKED', 'PENDING'))
);

-- ---------------------------------------------------------------------
-- 2. Barberías, clientes y empleados
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS barberia (
    id_barberia        UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre             VARCHAR(100) NOT NULL,
    descripcion        TEXT         NOT NULL,
    ubicacion          VARCHAR(150) NOT NULL,
    celular            VARCHAR(15)  NOT NULL,
    documento          VARCHAR(20)  NOT NULL,
    tipo_documento     VARCHAR(3)   NOT NULL,
    razon_social       VARCHAR(150),
    estado             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVO',
    fecha_creacion     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    fecha_modificacion TIMESTAMPTZ,

    CONSTRAINT uq_barberia_documento UNIQUE (tipo_documento, documento),
    CONSTRAINT ck_barberia_tipo_documento CHECK (tipo_documento IN ('CC', 'CE', 'NIT'))
);

CREATE TABLE IF NOT EXISTS cliente (
    id_cliente         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID         NOT NULL,
    documento          VARCHAR(20)  NOT NULL,
    tipo_documento     VARCHAR(3)   NOT NULL,
    nombre             VARCHAR(100) NOT NULL,
    celular            VARCHAR(15)  NOT NULL,
    correo             VARCHAR(150) NOT NULL,
    fecha_creacion     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    fecha_modificacion TIMESTAMPTZ,

    CONSTRAINT fk_cliente_usuario FOREIGN KEY (user_id)
        REFERENCES usuario (user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT uq_cliente_user_id   UNIQUE (user_id),
    CONSTRAINT uq_cliente_documento UNIQUE (tipo_documento, documento),
    CONSTRAINT uq_cliente_correo    UNIQUE (correo),
    CONSTRAINT ck_cliente_tipo_documento CHECK (tipo_documento IN ('CC', 'CE', 'NIT'))
);

-- Administradores y barberos. user_id puede ser nulo mientras el barbero
-- no tenga cuenta; sin cuenta ACTIVE con rol BARBER no es reservable.
CREATE TABLE IF NOT EXISTS empleado (
    id_empleado        UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID,
    id_barberia        UUID         NOT NULL,
    documento          VARCHAR(20)  NOT NULL,
    tipo_documento     VARCHAR(3)   NOT NULL,
    nombre             VARCHAR(100) NOT NULL,
    celular            VARCHAR(15)  NOT NULL,
    correo             VARCHAR(150) NOT NULL,
    especialidad       VARCHAR(100),
    fecha_creacion     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    fecha_modificacion TIMESTAMPTZ,

    CONSTRAINT fk_empleado_usuario FOREIGN KEY (user_id)
        REFERENCES usuario (user_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_empleado_barberia FOREIGN KEY (id_barberia)
        REFERENCES barberia (id_barberia) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT uq_empleado_user_id   UNIQUE (user_id),
    CONSTRAINT uq_empleado_documento UNIQUE (tipo_documento, documento),
    CONSTRAINT ck_empleado_tipo_documento CHECK (tipo_documento IN ('CC', 'CE', 'NIT'))
);

-- Un horario por empleado (el back lo consulta con findByEmpleadoId -> Mono).
CREATE TABLE IF NOT EXISTS horario_empleado (
    id_horario_empleado  INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    id_empleado          UUID    NOT NULL,
    hora_inicio          TIME    NOT NULL,
    hora_fin             TIME    NOT NULL,
    hora_inicio_almuerzo TIME,
    hora_fin_almuerzo    TIME,
    vacaciones           BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_horario_empleado_empleado FOREIGN KEY (id_empleado)
        REFERENCES empleado (id_empleado) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT uq_horario_empleado_empleado UNIQUE (id_empleado),
    CONSTRAINT ck_horario_empleado_horas CHECK (hora_inicio < hora_fin),
    CONSTRAINT ck_horario_empleado_almuerzo CHECK (
        hora_inicio_almuerzo IS NULL OR hora_fin_almuerzo IS NULL
        OR hora_inicio_almuerzo < hora_fin_almuerzo)
);

-- Historial de bloqueos y desbloqueos de cuentas (RF-16, RN-48).
CREATE TABLE IF NOT EXISTS bloqueo_usuario (
    id_bloqueo_usuario UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID        NOT NULL REFERENCES usuario (user_id) ON DELETE CASCADE,
    accion             VARCHAR(10) NOT NULL CHECK (accion IN ('BLOCK', 'UNBLOCK')),
    motivo             VARCHAR(200),
    realizado_por      UUID        REFERENCES usuario (user_id) ON DELETE SET NULL,
    fecha              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_bloqueo_usuario_motivo
        CHECK (accion <> 'BLOCK' OR char_length(trim(motivo)) BETWEEN 5 AND 200)
);

-- Festivos y cierres por barbería.
CREATE TABLE IF NOT EXISTS dia_no_laborable (
    id_dia_no_laborable UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    id_barberia         UUID        NOT NULL REFERENCES barberia (id_barberia) ON DELETE CASCADE,
    fecha               DATE        NOT NULL,
    motivo              VARCHAR(80) NOT NULL CHECK (char_length(trim(motivo)) BETWEEN 3 AND 80),
    creado_por          UUID,
    fecha_creacion      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_dia_no_laborable_barberia_fecha UNIQUE (id_barberia, fecha)
);

-- ---------------------------------------------------------------------
-- 3. Servicios, reservas y cola virtual
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS servicio (
    id_servicio        INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    id_barberia        UUID         NOT NULL,
    nombre             VARCHAR(100) NOT NULL,
    descripcion        TEXT         NOT NULL,
    duracion           INTEGER      NOT NULL,          -- minutos
    estado             BOOLEAN      NOT NULL DEFAULT TRUE,
    oferta             BOOLEAN      NOT NULL DEFAULT FALSE,
    precio             INTEGER      NOT NULL,
    precio_oferta      INTEGER,
    fecha_creacion     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    fecha_modificacion TIMESTAMPTZ,

    CONSTRAINT fk_servicio_barberia FOREIGN KEY (id_barberia)
        REFERENCES barberia (id_barberia) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT ck_servicio_duracion CHECK (duracion > 0),
    CONSTRAINT ck_servicio_precio CHECK (precio >= 0),
    CONSTRAINT ck_servicio_precio_oferta CHECK (
        precio_oferta IS NULL OR (precio_oferta >= 0 AND precio_oferta <= precio))
);

-- Reservas por agenda (SERVICE_RESERVATION) y turnos de cola (VIRTUAL_QUEUE).
-- motivo_cancelacion guarda las notas del cliente y el motivo al cancelar.
CREATE TABLE IF NOT EXISTS reserva (
    id_reserva         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    id_cliente         UUID        NOT NULL,
    id_empleado        UUID        NOT NULL,
    id_barberia        UUID,
    tipo_reserva       VARCHAR(20) NOT NULL,
    estado             VARCHAR(15) NOT NULL,
    duracion           INTEGER,                         -- minutos
    fecha_ingreso_cola TIMESTAMPTZ,
    fecha_inicio_real  TIMESTAMPTZ,
    fecha_fin_real     TIMESTAMPTZ,
    motivo_cancelacion TEXT,
    fecha_creacion     TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_modificacion TIMESTAMPTZ,

    CONSTRAINT fk_reserva_cliente FOREIGN KEY (id_cliente)
        REFERENCES cliente (id_cliente) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_reserva_empleado FOREIGN KEY (id_empleado)
        REFERENCES empleado (id_empleado) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_reserva_barberia FOREIGN KEY (id_barberia)
        REFERENCES barberia (id_barberia) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ck_reserva_tipo CHECK (tipo_reserva IN ('VIRTUAL_QUEUE', 'SERVICE_RESERVATION')),
    CONSTRAINT ck_reserva_estado CHECK (estado IN (
        'PENDING', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'WAITING', 'NO_SHOW')),
    CONSTRAINT ck_reserva_duracion CHECK (duracion IS NULL OR duracion >= 0),
    CONSTRAINT ck_reserva_fechas CHECK (
        fecha_inicio_real IS NULL OR fecha_fin_real IS NULL
        OR fecha_inicio_real <= fecha_fin_real)
);

-- Servicios de cada reserva; precio = lo cobrado al reservar (no el precio actual).
CREATE TABLE IF NOT EXISTS reserva_servicio (
    id_reserva_servicio UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    id_reserva          UUID        NOT NULL,
    id_servicio         INTEGER     NOT NULL,
    estado              VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    duracion            INTEGER     NOT NULL,
    precio              INTEGER,
    fecha_creacion      TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_modificacion  TIMESTAMPTZ,

    CONSTRAINT fk_reserva_servicio_reserva FOREIGN KEY (id_reserva)
        REFERENCES reserva (id_reserva) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_reserva_servicio_servicio FOREIGN KEY (id_servicio)
        REFERENCES servicio (id_servicio) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ck_reserva_servicio_estado CHECK (estado IN ('ACTIVE', 'COMPLETED', 'CANCEL')),
    CONSTRAINT ck_reserva_servicio_duracion CHECK (duracion >= 0),
    CONSTRAINT ck_reserva_servicio_precio CHECK (precio IS NULL OR precio >= 0)
);

COMMENT ON COLUMN reserva_servicio.precio IS
    'Precio cobrado al reservar (de oferta si el servicio estaba en oferta).';

-- Una calificación por reserva.
CREATE TABLE IF NOT EXISTS resena_reserva (
    id_resena          INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    id_reserva         UUID        NOT NULL,
    calificacion       INTEGER,
    comentario         TEXT,
    fecha_creacion     TIMESTAMPTZ DEFAULT now(),
    fecha_modificacion TIMESTAMPTZ,

    CONSTRAINT fk_resena_reserva FOREIGN KEY (id_reserva)
        REFERENCES reserva (id_reserva) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT uq_resena_reserva UNIQUE (id_reserva),
    CONSTRAINT ck_resena_calificacion CHECK (calificacion IS NULL OR calificacion BETWEEN 1 AND 5)
);

-- Estado de la cola virtual de cada barbero. La UNIQUE de id_empleado es
-- obligatoria: el back usa INSERT ... ON CONFLICT (id_empleado) DO NOTHING.
CREATE TABLE IF NOT EXISTS cola_barbero (
    id_cola_barbero    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    id_empleado        UUID        NOT NULL,
    id_barberia        UUID        NOT NULL,
    aceptando_clientes BOOLEAN     NOT NULL DEFAULT FALSE,
    version            BIGINT      NOT NULL DEFAULT 0,
    fecha_creacion     TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_modificacion TIMESTAMPTZ,

    CONSTRAINT fk_cola_barbero_empleado FOREIGN KEY (id_empleado)
        REFERENCES empleado (id_empleado) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_cola_barbero_barberia FOREIGN KEY (id_barberia)
        REFERENCES barberia (id_barberia) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT uq_cola_barbero_empleado UNIQUE (id_empleado)
);

-- Correos enviados al cliente sobre sus reservas.
CREATE TABLE IF NOT EXISTS notificacion (
    id_notificacion  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    id_cliente       UUID         NOT NULL,
    id_reserva       UUID,
    titulo           VARCHAR(150) NOT NULL,
    contenido        TEXT         NOT NULL,
    tipo_canal       VARCHAR(20)  NOT NULL,            -- EMAIL
    tipo_evento      VARCHAR(50)  NOT NULL,            -- RESERVATION_CREATED, ...
    estado           VARCHAR(15)  NOT NULL,
    reintentos       INTEGER      NOT NULL DEFAULT 0,
    error_mensaje    TEXT,
    fecha_programada TIMESTAMPTZ,
    fecha_envio      TIMESTAMPTZ,
    fecha_creacion   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_notificacion_cliente FOREIGN KEY (id_cliente)
        REFERENCES cliente (id_cliente) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_notificacion_reserva FOREIGN KEY (id_reserva)
        REFERENCES reserva (id_reserva) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT ck_notificacion_estado CHECK (estado IN ('PENDING', 'SENT', 'FAILED')),
    CONSTRAINT ck_notificacion_reintentos CHECK (reintentos >= 0)
);

-- ---------------------------------------------------------------------
-- 4. Suscripciones y pagos (pasarela)
-- ---------------------------------------------------------------------

-- Planes. descuento en porcentaje (0-100).
CREATE TABLE IF NOT EXISTS suscripcion (
    id_suscripcion      INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    nombre              VARCHAR(50) NOT NULL,
    descripcion         TEXT        NOT NULL,
    precio              INTEGER     NOT NULL,
    descuento           SMALLINT    NOT NULL DEFAULT 0,
    fecha_creacion      TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_actualizacion TIMESTAMPTZ,

    CONSTRAINT uq_suscripcion_nombre UNIQUE (nombre),
    CONSTRAINT ck_suscripcion_precio CHECK (precio >= 0),
    CONSTRAINT ck_suscripcion_descuento CHECK (descuento BETWEEN 0 AND 100)
);

-- Periodos contratados por cada barbería. fecha_inicio / fecha_expiracion
-- quedan nulas mientras el pago está PENDING.
CREATE TABLE IF NOT EXISTS suscripcion_barberia (
    id_suscripcion_barberia UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    id_barberia             UUID        NOT NULL,
    id_suscripcion          INTEGER     NOT NULL,
    estado                  VARCHAR(20) NOT NULL,
    dias_duracion           INTEGER     NOT NULL,
    precio                  INTEGER     NOT NULL,
    descuento               SMALLINT    NOT NULL DEFAULT 0,
    -- Nullable: viene de la petición y R2DBC inserta el nulo explícito
    -- (el DEFAULT no aplica); el back trata nulo como "sin renovación".
    renovacion_automatica   BOOLEAN     DEFAULT FALSE,
    fecha_inicio            TIMESTAMPTZ,
    fecha_expiracion        TIMESTAMPTZ,
    fecha_creacion          TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_modificacion      TIMESTAMPTZ,

    CONSTRAINT fk_suscripcion_barberia_barberia FOREIGN KEY (id_barberia)
        REFERENCES barberia (id_barberia) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_suscripcion_barberia_suscripcion FOREIGN KEY (id_suscripcion)
        REFERENCES suscripcion (id_suscripcion) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ck_suscripcion_barberia_estado CHECK (estado IN (
        'ACTIVE', 'APPROVED', 'PENDING', 'DECLINED', 'NOT_SUBSCRIPTION', 'EXPIRED')),
    CONSTRAINT ck_suscripcion_barberia_dias CHECK (dias_duracion > 0),
    CONSTRAINT ck_suscripcion_barberia_precio CHECK (precio >= 0),
    CONSTRAINT ck_suscripcion_barberia_descuento CHECK (descuento BETWEEN 0 AND 100),
    CONSTRAINT ck_suscripcion_barberia_vigencia CHECK (
        fecha_inicio IS NULL OR fecha_expiracion IS NULL OR fecha_inicio < fecha_expiracion)
);

-- Pagos: de una reserva o de una suscripción (exactamente uno de los dos).
-- id_orden es la referencia que viaja a la pasarela y vuelve en su respuesta.
CREATE TABLE IF NOT EXISTS transaccion (
    id_transaccion          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    id_reserva              UUID,
    id_suscripcion_barberia UUID,
    total                   INTEGER     NOT NULL,
    descuento               INTEGER     NOT NULL DEFAULT 0,
    subtotal                INTEGER     NOT NULL,
    estado                  VARCHAR(10) NOT NULL,
    id_orden                UUID        NOT NULL,
    fecha_creacion          TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_modificacion      TIMESTAMPTZ,

    CONSTRAINT fk_transaccion_reserva FOREIGN KEY (id_reserva)
        REFERENCES reserva (id_reserva) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_transaccion_suscripcion_barberia FOREIGN KEY (id_suscripcion_barberia)
        REFERENCES suscripcion_barberia (id_suscripcion_barberia) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT uq_transaccion_orden UNIQUE (id_orden),
    CONSTRAINT uq_transaccion_suscripcion_barberia UNIQUE (id_suscripcion_barberia),
    CONSTRAINT ck_transaccion_origen CHECK (num_nonnulls(id_reserva, id_suscripcion_barberia) = 1),
    CONSTRAINT ck_transaccion_estado CHECK (estado IN (
        'PENDING', 'APPROVED', 'REJECTED', 'CANCELED', 'REFUNDED')),
    CONSTRAINT ck_transaccion_montos CHECK (total >= 0 AND descuento >= 0 AND subtotal >= 0)
);

-- ---------------------------------------------------------------------
-- 4b. Penalización de clientes (inasistencias y restricciones)
-- ---------------------------------------------------------------------

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

-- ---------------------------------------------------------------------
-- 5. Índices (consultas frecuentes, estadísticas y jobs de suscripción)
-- ---------------------------------------------------------------------

CREATE INDEX IF NOT EXISTS idx_usuario_id_rol            ON usuario (id_rol);
CREATE INDEX IF NOT EXISTS idx_empleado_barberia         ON empleado (id_barberia);
CREATE INDEX IF NOT EXISTS idx_servicio_id_barberia      ON servicio (id_barberia);
CREATE INDEX IF NOT EXISTS idx_bloqueo_usuario_user_fecha ON bloqueo_usuario (user_id, fecha DESC);

CREATE INDEX IF NOT EXISTS idx_reserva_id_cliente        ON reserva (id_cliente);
CREATE INDEX IF NOT EXISTS idx_reserva_id_barberia       ON reserva (id_barberia);
CREATE INDEX IF NOT EXISTS idx_reserva_fecha_creacion    ON reserva (fecha_creacion);
-- Choques de agenda: id_empleado + rango de fecha_inicio_real / fecha_fin_real.
CREATE INDEX IF NOT EXISTS idx_reserva_empleado_inicio   ON reserva (id_empleado, fecha_inicio_real);
-- Cola virtual: turnos en espera por orden de llegada.
CREATE INDEX IF NOT EXISTS idx_reserva_cola
    ON reserva (id_empleado, fecha_ingreso_cola, id_reserva)
    WHERE tipo_reserva = 'VIRTUAL_QUEUE';
-- Estadísticas: filtran por el mismo COALESCE.
CREATE INDEX IF NOT EXISTS idx_reserva_empleado_momento
    ON reserva (id_empleado, (COALESCE(fecha_inicio_real, fecha_ingreso_cola)));

CREATE INDEX IF NOT EXISTS idx_reserva_servicio_reserva  ON reserva_servicio (id_reserva);
CREATE INDEX IF NOT EXISTS idx_reserva_servicio_servicio ON reserva_servicio (id_servicio);
CREATE INDEX IF NOT EXISTS idx_cola_barbero_barberia     ON cola_barbero (id_barberia);

CREATE INDEX IF NOT EXISTS idx_notificacion_cliente      ON notificacion (id_cliente);
CREATE INDEX IF NOT EXISTS idx_notificacion_reserva      ON notificacion (id_reserva);
CREATE INDEX IF NOT EXISTS idx_notificacion_estado_fecha ON notificacion (estado, fecha_programada);

CREATE INDEX IF NOT EXISTS idx_suscripcion_barberia_barberia_estado
    ON suscripcion_barberia (id_barberia, estado, fecha_creacion DESC);
CREATE INDEX IF NOT EXISTS idx_suscripcion_barberia_estado_expiracion
    ON suscripcion_barberia (estado, fecha_expiracion);

CREATE INDEX IF NOT EXISTS idx_transaccion_reserva       ON transaccion (id_reserva);

-- ---------------------------------------------------------------------
-- 6. Datos iniciales
-- ---------------------------------------------------------------------

INSERT INTO rol (id_rol, nombre, estado) VALUES
    (1, 'ADMIN',    'ACTIVE'),
    (2, 'BARBER',   'ACTIVE'),
    (3, 'CUSTOMER', 'ACTIVE')
ON CONFLICT (id_rol) DO NOTHING;

INSERT INTO suscripcion (nombre, descripcion, precio, descuento) VALUES
    ('PLAN PREMIUM', 'Plan premium para barberías con funcionalidades esenciales.', 45000, 0)
ON CONFLICT (nombre) DO NOTHING;

-- ---------------------------------------------------------------------
-- Verificación: deben aparecer las 19 tablas.
-- SELECT table_name FROM information_schema.tables
--  WHERE table_schema = 'public' ORDER BY table_name;
-- ---------------------------------------------------------------------
