package com.smartbarber.infrastructure.entrypoint.scheduler;

import com.smartbarber.application.usecase.penalty.CustomerPenaltyUC;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Vence las restricciones de clientes cuya fecha de fin ya pasó y les avisa
 * que pueden volver a reservar (PEN-12). La validación al reservar ya trata
 * como vencida una restricción pasada su fecha, aunque esta tarea no haya
 * corrido todavía.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PenaltyRestrictionScheduler {

    private final CustomerPenaltyUC customerPenaltyUC;

    @Scheduled(cron = "${penalty.expiration.cron:0 10 * * * *}", zone = "America/Bogota")
    public void expireRestrictions() {
        customerPenaltyUC.expireDue()
                .subscribe(
                        total -> log.info("Restricciones de clientes vencidas: {}", total),
                        error -> log.error("Vencimiento de restricciones interrumpido", error));
    }
}
