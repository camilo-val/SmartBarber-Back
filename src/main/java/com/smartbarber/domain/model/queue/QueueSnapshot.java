package com.smartbarber.domain.model.queue;

import com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.customer.CustomerRsDto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Estado de la cola virtual de un barbero.
 *
 * <p>La vista completa (con nombres, notas y servicios de cada cliente) es
 * solo para el personal de la barbería. Un cliente recibe
 * {@link #forCustomer(UUID)}: su propio turno completo y el de los demás
 * como "Turno #N", sin datos personales.</p>
 */
public record QueueSnapshot(
        UUID employeeId,
        boolean acceptingClients,
        QueueTurn currentTurn,
        List<QueueTurn> waitingTurns,
        OffsetDateTime updatedAt
) {

    public record QueueTurn(
            UUID reservationId,
            CustomerRsDto customer,
            int position,
            int estimatedWaitMinutes,
            Integer durationMinutes,
            String status,
            /** Nombres de los servicios del turno. */
            List<String> services,
            /** Nota que dejó el cliente al unirse. */
            String notes,
            /** Tipo de reserva (VIRTUAL_QUEUE para los turnos de la cola). */
            String reservationType,
            /** El turno es del cliente que consulta (solo en la vista de cliente). */
            boolean mine
    ) {
        /** El turno visto por otro cliente: sin nombre, servicios ni nota. */
        QueueTurn anonymized() {
            return new QueueTurn(reservationId, null, position, estimatedWaitMinutes,
                    durationMinutes, status, List.of(), null, reservationType, false);
        }

        QueueTurn markedAsMine() {
            return new QueueTurn(reservationId, customer, position, estimatedWaitMinutes,
                    durationMinutes, status, services, notes, reservationType, true);
        }

        boolean belongsTo(UUID customerId) {
            return customerId != null && customer != null && Objects.equals(customer.id(), customerId);
        }
    }

    /**
     * Vista para un cliente. Con {@code customerId} null (cliente sin
     * identificar) se ocultan los datos de todos los turnos.
     */
    public QueueSnapshot forCustomer(UUID customerId) {
        return new QueueSnapshot(
                employeeId,
                acceptingClients,
                currentTurn == null ? null : visibleTo(currentTurn, customerId),
                waitingTurns.stream().map(turn -> visibleTo(turn, customerId)).toList(),
                updatedAt);
    }

    private static QueueTurn visibleTo(QueueTurn turn, UUID customerId) {
        return turn.belongsTo(customerId) ? turn.markedAsMine() : turn.anonymized();
    }
}
