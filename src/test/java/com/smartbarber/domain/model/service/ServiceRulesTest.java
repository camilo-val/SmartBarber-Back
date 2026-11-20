package com.smartbarber.domain.model.service;

import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.service.ServiceMessageExceptions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceRulesTest {

    private static Service service() {
        return Service.createService(null, UUID.randomUUID(), "Corte", "Corte clásico", 30, 40000, false, null);
    }

    @Test
    void elPrecioDeOfertaDebeSerMenorQueElNormal() {
        BusinessExceptions error = assertThrows(BusinessExceptions.class, () ->
                Service.createService(null, UUID.randomUUID(), "Corte", "Corte clásico", 30, 40000, true, 50000));
        assertEquals(ServiceMessageExceptions.OFFER_NOT_LOWER, error.getExceptionMessage());
    }

    @Test
    void seDesactivaYSeVuelveAActivar() {
        Service inactive = service().withStatus(false);
        assertFalse(inactive.isActive());
        assertTrue(inactive.withStatus(true).isActive());
    }
}
