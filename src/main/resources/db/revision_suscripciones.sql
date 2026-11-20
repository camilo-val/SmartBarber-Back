-- =====================================================================
-- Revisión de suscripciones inconsistentes (reporte de errores 20/11, error 31)
--
-- El back ya no genera estos casos (vence solo las suscripciones con la fecha
-- cumplida, la duración la fija el plan y la renovación aprobada se activa al
-- vencer la anterior). Este script sirve para revisar y corregir los registros
-- que quedaron mal antes de esos cambios.
--
-- 1) Ejecuta solo las consultas de DIAGNÓSTICO y revisa los resultados.
-- 2) Las CORRECCIONES están comentadas: quita el comentario solo después de
--    confirmar cada caso con el equipo (y con quien maneja la pasarela para
--    los pagos pendientes). Ejecútalas dentro de una transacción.
-- =====================================================================

-- DIAGNÓSTICO A: vencidas con fecha de vencimiento futura.
SELECT id_suscripcion_barberia, id_barberia, estado, fecha_inicio, fecha_expiracion
  FROM public.suscripcion_barberia
 WHERE estado = 'EXPIRED'
   AND fecha_expiracion > now();

-- DIAGNÓSTICO B: barberías con más de una suscripción ACTIVE.
SELECT id_barberia, COUNT(*) AS activas
  FROM public.suscripcion_barberia
 WHERE estado = 'ACTIVE'
 GROUP BY id_barberia
HAVING COUNT(*) > 1;

-- DIAGNÓSTICO C: periodos con más de 31 días de vigencia.
SELECT id_suscripcion_barberia, id_barberia, estado, dias_duracion, fecha_inicio, fecha_expiracion
  FROM public.suscripcion_barberia
 WHERE fecha_expiracion - fecha_inicio > interval '31 days';

-- DIAGNÓSTICO D: pagos pendientes con más de 2 días.
SELECT id_suscripcion_barberia, id_barberia, estado, fecha_creacion
  FROM public.suscripcion_barberia
 WHERE estado = 'PENDING'
   AND fecha_creacion < now() - interval '2 days';

-- ------------------------------------------------------------------
-- CORRECCIONES (comentadas a propósito)
-- ------------------------------------------------------------------
-- BEGIN;
--
-- -- A: una suscripción vencida por error con fecha futura vuelve a ACTIVE
-- --    solo si la barbería no tiene otra ACTIVE.
-- UPDATE public.suscripcion_barberia s
--    SET estado = 'ACTIVE'
--  WHERE s.estado = 'EXPIRED'
--    AND s.fecha_expiracion > now()
--    AND NOT EXISTS (SELECT 1 FROM public.suscripcion_barberia o
--                     WHERE o.id_barberia = s.id_barberia AND o.estado = 'ACTIVE');
--
-- -- D: pagos pendientes abandonados se marcan como rechazados
-- --    (confirmar antes en la pasarela que no se cobraron).
-- UPDATE public.suscripcion_barberia
--    SET estado = 'DECLINED'
--  WHERE estado = 'PENDING'
--    AND fecha_creacion < now() - interval '2 days';
--
-- COMMIT;
