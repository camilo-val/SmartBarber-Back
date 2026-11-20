package com.smartbarber.domain.model.queue;

import com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.customer.CustomerRsDto;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Un cliente ve su turno completo y los demás sin datos personales. */
class QueueSnapshotPrivacyTest {

    private static final UUID ME = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    private static QueueSnapshot.QueueTurn turn(UUID customerId, String name, int position) {
        CustomerRsDto customer = CustomerRsDto.builder().id(customerId).name(name).build();
        return new QueueSnapshot.QueueTurn(UUID.randomUUID(), customer, position, position * 10, 30,
                "WAITING", List.of("Corte"), "Nota privada", "VIRTUAL_QUEUE", false);
    }

    private static QueueSnapshot snapshot() {
        return new QueueSnapshot(UUID.randomUUID(), true, null,
                List.of(turn(OTHER, "Otro Cliente", 1), turn(ME, "Yo Mismo", 2)), OffsetDateTime.now());
    }

    @Test
    void elClienteVeSoloSuTurnoConDatos() {
        QueueSnapshot view = snapshot().forCustomer(ME);

        QueueSnapshot.QueueTurn other = view.waitingTurns().get(0);
        assertNull(other.customer());
        assertNull(other.notes());
        assertTrue(other.services().isEmpty());
        assertFalse(other.mine());
        assertEquals(1, other.position());

        QueueSnapshot.QueueTurn mine = view.waitingTurns().get(1);
        assertTrue(mine.mine());
        assertEquals("Yo Mismo", mine.customer().name());
        assertEquals(20, mine.estimatedWaitMinutes());
    }

    @Test
    void sinClienteIdentificadoSeOcultaTodo() {
        QueueSnapshot view = snapshot().forCustomer(null);
        assertTrue(view.waitingTurns().stream().allMatch(turn -> turn.customer() == null && !turn.mine()));
    }
}
