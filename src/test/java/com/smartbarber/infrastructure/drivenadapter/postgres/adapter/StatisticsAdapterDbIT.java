package com.smartbarber.infrastructure.drivenadapter.postgres.adapter;

import com.smartbarber.domain.enums.ReservationStatus;
import com.smartbarber.domain.model.statistics.BarberAgenda;
import com.smartbarber.domain.model.statistics.ReservationFact;
import com.smartbarber.domain.model.statistics.StatisticsCalculator;
import com.smartbarber.domain.model.statistics.StatisticsDashboard;
import com.smartbarber.domain.model.statistics.StatisticsRange;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.Option;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.r2dbc.core.DatabaseClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba de integración de SOLO LECTURA contra la base configurada en DB_URL,
 * DB_USERNAME y DB_PASSWORD. Valida que el SQL de estadísticas funcione con el
 * esquema real. Solo se ejecuta con RUN_DB_IT=true y la sesión se abre con
 * default_transaction_read_only=on: la base rechaza cualquier escritura.
 * El resultado se guarda en build/db-it-report.txt.
 */
@EnabledIfEnvironmentVariable(named = "RUN_DB_IT", matches = "true")
class StatisticsAdapterDbIT {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private static DatabaseClient db;
    private static final List<String> report = new ArrayList<>();

    @BeforeAll
    static void connect() {
        ConnectionFactoryOptions options = ConnectionFactoryOptions.parse(System.getenv("DB_URL"))
                .mutate()
                .option(ConnectionFactoryOptions.USER, System.getenv("DB_USERNAME"))
                .option(ConnectionFactoryOptions.PASSWORD, System.getenv("DB_PASSWORD"))
                .option(Option.valueOf("options"), Map.of("default_transaction_read_only", "on"))
                .build();
        ConnectionFactory factory = ConnectionFactories.get(options);
        db = DatabaseClient.create(factory);
    }

    @Test
    void theSessionIsReadOnly() {
        String readOnly = db.sql("SHOW default_transaction_read_only")
                .map((row, meta) -> row.get(0, String.class)).one().block();
        assertEquals("on", readOnly);
    }

    @Test
    void statisticsQueriesWorkAgainstTheRealSchema() throws IOException {
        // Barbería con más reservas en el último año.
        Map<String, Object> shop = db.sql("""
                        SELECT e.id_barberia, COUNT(*) AS total
                          FROM public.reserva r
                          JOIN public.empleado e ON e.id_empleado = r.id_empleado
                         GROUP BY e.id_barberia
                         ORDER BY total DESC
                         LIMIT 1
                        """)
                .fetch().one().block();
        assertNotNull(shop, "no hay reservas en la base");
        UUID barbershopId = (UUID) shop.get("id_barberia");
        report.add("Barbería con más reservas: " + barbershopId + " (" + shop.get("total") + " reservas en total)");

        LocalDate today = LocalDate.now(BOGOTA);
        StatisticsRange range = new StatisticsRange(today.minusDays(364), today);
        LocalDate first = range.previous().from();

        StatisticsAdapter adapter = new StatisticsAdapter(db);
        List<ReservationFact> facts = adapter.findReservationFacts(
                        barbershopId,
                        first.atStartOfDay(BOGOTA).toOffsetDateTime(),
                        today.plusDays(1).atStartOfDay(BOGOTA).toOffsetDateTime())
                .collectList().block();
        assertNotNull(facts);

        report.add("Reservas leídas por StatisticsAdapter (periodo + anterior): " + facts.size());
        report.add("Por estado: " + facts.stream().collect(Collectors.groupingBy(
                f -> String.valueOf(f.status()), Collectors.counting())));
        report.add("Por tipo: " + facts.stream().collect(Collectors.groupingBy(
                f -> String.valueOf(f.type()), Collectors.counting())));
        report.add("Con servicios: " + facts.stream().filter(f -> !f.services().isEmpty()).count()
                + " | con calificación: " + facts.stream().filter(f -> f.rating() != null).count()
                + " | sin fecha: " + facts.stream().filter(f -> f.occurredAt() == null).count());
        assertTrue(facts.stream().allMatch(f -> f.status() != null && f.type() != null),
                "hay estados o tipos que no reconoce el backend");

        // Equipo y jornadas tal como los lee el caso de uso.
        List<BarberAgenda> barbers = db.sql("""
                        SELECT e.id_empleado, e.nombre, h.hora_inicio, h.hora_fin,
                               h.hora_inicio_almuerzo, h.hora_fin_almuerzo
                          FROM public.empleado e
                          LEFT JOIN public.horario_empleado h ON h.id_empleado = e.id_empleado
                         WHERE e.id_barberia = :shop
                        """)
                .bind("shop", barbershopId)
                .map((row, meta) -> new BarberAgenda(
                        row.get("id_empleado", UUID.class),
                        row.get("nombre", String.class),
                        row.get("hora_inicio", LocalTime.class),
                        row.get("hora_fin", LocalTime.class),
                        row.get("hora_inicio_almuerzo", LocalTime.class),
                        row.get("hora_fin_almuerzo", LocalTime.class)))
                .all().collectList().block();

        Set<LocalDate> closedDays = db.sql("""
                        SELECT fecha FROM public.dia_no_laborable
                         WHERE id_barberia = :shop AND fecha BETWEEN :from AND :to
                        """)
                .bind("shop", barbershopId).bind("from", first).bind("to", today)
                .map((row, meta) -> row.get("fecha", LocalDate.class))
                .all().collect(Collectors.toSet()).block();

        StatisticsDashboard dashboard = StatisticsCalculator.calculate(new StatisticsCalculator.Input(
                barbershopId, range, facts, barbers, closedDays, BOGOTA, Instant.now()));

        StatisticsDashboard.PeriodTotals totals = dashboard.current();
        report.add("Equipo: " + barbers.size() + " empleados, "
                + barbers.stream().filter(BarberAgenda::hasSchedule).count() + " con horario | días no laborables: " + closedDays.size());
        report.add(String.format("Último año → ingresos: %,d | finalizados: %d | ocupación: %.2f%% | efectivas: %s | espera cola: %s min",
                totals.revenue(), totals.completed(), totals.occupancy() * 100,
                totals.effectiveRate() == null ? "sin datos" : String.format("%.1f%%", totals.effectiveRate() * 100),
                totals.averageWait() == null ? "sin datos" : String.format("%.1f", totals.averageWait())));
        report.add("Servicios más pedidos: " + dashboard.revenueByService().stream().limit(5)
                .map(s -> s.name() + " (" + s.count() + ", $" + s.revenue() + ")").toList());
        report.add("Horas pico: " + dashboard.peakSlots().stream()
                .map(c -> "día " + c.weekday() + " " + c.hour() + "h=" + c.reservations()).toList());
        report.add("Comportamiento: " + dashboard.customerBehavior());
        report.add("Cola: " + dashboard.virtualQueue().turnsServed() + " atendidos, "
                + dashboard.virtualQueue().freedSlots() + " espacios liberados, "
                + dashboard.virtualQueue().recoveredSlots() + " recuperados");
        long completedWithoutServices = facts.stream()
                .filter(f -> f.status() == ReservationStatus.COMPLETED && f.services().isEmpty()).count();
        report.add("Finalizadas sin servicios (no suman ingreso): " + completedWithoutServices);

        assertEquals(range.days(), dashboard.revenueByDay().size());
        write();
    }

    @Test
    void blockHistoryTableStatus() throws IOException {
        Boolean exists = db.sql("SELECT to_regclass('public.bloqueo_usuario') IS NOT NULL AS existe")
                .map((row, meta) -> row.get("existe", Boolean.class)).one().block();
        String estadoType = db.sql("""
                        SELECT data_type || '/' || udt_name AS tipo FROM information_schema.columns
                         WHERE table_name = 'usuario' AND column_name = 'estado'
                        """)
                .map((row, meta) -> row.get("tipo", String.class)).one().block();
        Map<String, Long> statuses = db.sql("SELECT estado, COUNT(*) AS n FROM public.usuario GROUP BY estado")
                .map((row, meta) -> Map.entry(String.valueOf(row.get("estado", String.class)), row.get("n", Long.class)))
                .all().collectMap(Map.Entry::getKey, Map.Entry::getValue).block();

        report.add("Tabla bloqueo_usuario: " + (Boolean.TRUE.equals(exists) ? "EXISTE" : "NO EXISTE (falta ejecutar bloqueo_usuario.sql)"));
        report.add("Columna usuario.estado: " + estadoType + " | usuarios por estado: " + statuses);

        if (Boolean.TRUE.equals(exists)) {
            // Misma consulta que AccessChangeData.findLatestByUserIds.
            Long rows = db.sql("""
                            SELECT COUNT(*) FROM (
                              SELECT DISTINCT ON (user_id) * FROM public.bloqueo_usuario
                               ORDER BY user_id, fecha DESC) t
                            """)
                    .map((row, meta) -> row.get(0, Long.class)).one().block();
            report.add("Último cambio por usuario (DISTINCT ON): " + rows + " filas");

            // Las columnas deben coincidir con AccessChangeEntity.
            List<String> columns = db.sql("""
                            SELECT column_name || ':' || data_type AS col FROM information_schema.columns
                             WHERE table_schema = 'public' AND table_name = 'bloqueo_usuario'
                             ORDER BY ordinal_position
                            """)
                    .map((row, meta) -> row.get("col", String.class)).all().collectList().block();
            List<String> indexes = db.sql("SELECT indexname FROM pg_indexes WHERE tablename = 'bloqueo_usuario'")
                    .map((row, meta) -> row.get("indexname", String.class)).all().collectList().block();
            report.add("Columnas: " + columns);
            report.add("Índices: " + indexes);
            assertEquals(List.of("id_bloqueo_usuario", "user_id", "accion", "motivo", "realizado_por", "fecha"),
                    columns.stream().map(c -> c.substring(0, c.indexOf(':'))).toList());
            assertTrue(indexes.contains("idx_bloqueo_usuario_user_fecha"), "falta el índice (user_id, fecha DESC)");
        }
        write();
    }

    private static synchronized void write() throws IOException {
        Path file = Path.of("build", "db-it-report.txt");
        Files.createDirectories(file.getParent());
        Files.write(file, report);
    }
}
