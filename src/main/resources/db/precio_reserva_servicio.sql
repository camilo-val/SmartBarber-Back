-- =====================================================================
-- Precio cobrado por cada servicio reservado (reserva_servicio.precio)
--
-- OBLIGATORIO antes de desplegar el back que guarda el precio: la
-- aplicación escribe y lee esta columna al crear reservas.
--
-- Por qué: las estadísticas calculaban los ingresos con el precio ACTUAL
-- del servicio, así que al cambiar un precio cambiaban también los
-- ingresos de meses pasados. Desde ahora cada reserva guarda lo cobrado.
--
-- Es idempotente: se puede ejecutar más de una vez sin efectos extra.
-- =====================================================================

BEGIN;

ALTER TABLE public.reserva_servicio
    ADD COLUMN IF NOT EXISTS precio INTEGER;

COMMENT ON COLUMN public.reserva_servicio.precio IS
    'Precio cobrado al reservar (de oferta si el servicio estaba en oferta).';

-- Reservas existentes: se completa con el precio vigente del servicio, que es
-- la mejor aproximación disponible (el precio original no se guardó).
UPDATE public.reserva_servicio rs
   SET precio = CASE WHEN s.oferta AND s.precio_oferta IS NOT NULL
                     THEN s.precio_oferta
                     ELSE s.precio END
  FROM public.servicio s
 WHERE s.id_servicio = rs.id_servicio
   AND rs.precio IS NULL;

COMMIT;

-- Verificación: debe devolver 0.
-- SELECT COUNT(*) FROM public.reserva_servicio WHERE precio IS NULL;
