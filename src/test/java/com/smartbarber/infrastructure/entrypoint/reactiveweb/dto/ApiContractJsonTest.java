package com.smartbarber.infrastructure.entrypoint.reactiveweb.dto;

import com.smartbarber.domain.enums.DocumentType;
import com.smartbarber.domain.enums.ReservationStatus;
import com.smartbarber.domain.enums.ReservationType;
import com.smartbarber.domain.enums.RoleType;
import com.smartbarber.domain.enums.UserStatus;
import com.smartbarber.domain.model.employee.Employee;
import com.smartbarber.domain.model.employee.TeamMember;
import com.smartbarber.domain.model.statistics.BarberAgenda;
import com.smartbarber.domain.model.statistics.ReservationFact;
import com.smartbarber.domain.model.statistics.StatisticsCalculator;
import com.smartbarber.domain.model.statistics.StatisticsRange;
import com.smartbarber.domain.model.user.AccessChange;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.employee.TeamMemberRsDto;
import com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.statistics.StatisticsDashboardRsDto;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica que el JSON de las respuestas tenga los nombres y formatos que
 * espera el front (statistics-api.mapper.ts y employee.mapper.ts).
 */
class ApiContractJsonTest {

    private final ObjectMapper json = JsonMapper.builder().build();

    @Test
    void statisticsDashboardMatchesTheFrontContract() {
        ZoneId bogota = ZoneId.of("America/Bogota");
        LocalDate today = LocalDate.of(2026, 11, 12);
        UUID barber = UUID.randomUUID();
        ReservationFact fact = new ReservationFact(UUID.randomUUID(), ReservationType.SERVICE_RESERVATION,
                ReservationStatus.COMPLETED, barber, UUID.randomUUID(), 30, null,
                today.atTime(10, 0).atZone(bogota).toOffsetDateTime(),
                today.atTime(10, 30).atZone(bogota).toOffsetDateTime(), 5.0,
                List.of(new ReservationFact.ServiceLine(1, "Corte", 25_000, 30)));

        StatisticsDashboardRsDto dto = StatisticsDashboardRsDto.from(StatisticsCalculator.calculate(
                new StatisticsCalculator.Input(UUID.randomUUID(), new StatisticsRange(today, today), List.of(fact),
                        List.of(new BarberAgenda(barber, "Carlos", LocalTime.of(9, 0), LocalTime.of(18, 0), null, null)),
                        Set.of(), bogota, Instant.parse("2026-11-12T20:00:00Z"))));

        JsonNode body = json.valueToTree(dto);

        assertEquals("2026-11-12", body.path("range").path("from").asString());
        assertEquals("2026-11-12T20:00:00Z", body.path("generatedAt").asString());
        assertEquals(25_000, body.path("current").path("revenue").asLong());
        assertTrue(body.path("current").has("averageWait"));
        assertTrue(body.path("previous").isNull());
        assertEquals("2026-11-12", body.path("revenueByDay").get(0).path("key").asString());
        assertEquals(1, body.path("revenueByService").get(0).path("serviceId").asInt());
        assertEquals("Carlos", body.path("revenueByBarber").get(0).path("name").asString());
        assertEquals(barber.toString(), body.path("occupancyByBarber").get(0).path("barberId").asString());
        assertTrue(body.path("demand").get(0).has("weekday"));
        assertEquals(5.0, body.path("barberPerformance").get(0).path("rating").asDouble());
        assertEquals(1, body.path("customerBehavior").path("effective").asInt());
        assertTrue(body.path("virtualQueue").path("waitByHour").isArray());
    }

    @Test
    void teamMemberMatchesTheFrontContract() {
        Employee employee = Employee.rebuild(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "1012345678",
                DocumentType.CC, "Carlos", "3001234567", "carlos@correo.com", "Corte", Instant.now(), null);

        AccessChange block = new AccessChange(UUID.randomUUID(), employee.getUserId(), AccessChange.Action.BLOCK,
                "No se presentó", UUID.randomUUID(), Instant.parse("2026-11-12T15:00:00Z"));
        JsonNode body = json.valueToTree(TeamMemberRsDto.from(
                new TeamMember(employee, UserStatus.BLOCKED, RoleType.BARBER, block)));

        assertEquals("BLOCKED", body.path("status").asString());
        assertEquals("BARBER", body.path("role").asString());
        assertEquals(employee.getId().toString(), body.path("id").asString());
        assertEquals("CC", body.path("documentType").asString());
        assertEquals("No se presentó", body.path("blockReason").asString());
        assertEquals("2026-11-12T15:00:00Z", body.path("blockedAt").asString());
    }
}
