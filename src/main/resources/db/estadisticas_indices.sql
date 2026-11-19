-- =====================================================================
-- Índices para las estadísticas (GET /statistics-service/dashboard)
-- OPCIONAL: el tablero funciona sin ellos, pero con muchas reservas las
-- consultas por barbería y rango de fechas son mucho más rápidas (RNF-13).
-- Es idempotente (IF NOT EXISTS) y no cambia datos ni columnas.
--
-- Antes de ejecutar, revisa si ya existen índices equivalentes (por ejemplo,
-- creados por una llave foránea) para no duplicarlos:
--   SELECT tablename, indexname, indexdef FROM pg_indexes
--    WHERE tablename IN ('reserva', 'reserva_servicio', 'resena_reserva', 'empleado')
--    ORDER BY tablename;
-- =====================================================================

-- Reservas de cada barbero en un rango: la consulta filtra por el mismo COALESCE.
CREATE INDEX IF NOT EXISTS idx_reserva_empleado_momento
    ON public.reserva (id_empleado, (COALESCE(fecha_inicio_real, fecha_ingreso_cola)));

-- Servicios y calificación de cada reserva.
CREATE INDEX IF NOT EXISTS idx_reserva_servicio_reserva
    ON public.reserva_servicio (id_reserva);

CREATE INDEX IF NOT EXISTS idx_resena_reserva_reserva
    ON public.resena_reserva (id_reserva);

-- Equipo de una barbería.
CREATE INDEX IF NOT EXISTS idx_empleado_barberia
    ON public.empleado (id_barberia);
