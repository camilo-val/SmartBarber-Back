package com.smartbarber.domain.model.service;

import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.service.ServiceMessageExceptions;

import java.time.Instant;
import java.util.UUID;

public class Service {

    private final Integer id;
    private final UUID barberiaId;
    private final String name;
    private final String description;
    private final Integer duration;
    private final Boolean status;
    private final Boolean offer;
    private final Integer price;
    private final Integer specialPrice;
    private final Instant createAt;
    private final Instant updateAt;

    private Service(
            Integer id,
            UUID barberiaId,
            String name,
            String description,
            Integer duration,
            Boolean status,
            Boolean offer,
            Integer price,
            Integer specialPrice,
            Instant createAt,
            Instant updateAt
    ) {
        this.id = id;
        this.barberiaId = barberiaId;
        this.name = name;
        this.description = description;
        this.duration = duration;
        this.status = status;
        this.offer = offer;
        this.price = price;
        this.specialPrice = specialPrice;
        this.createAt = createAt;
        this.updateAt = updateAt;
    }

    public static Service createService(
            Integer id,
            UUID barberiaId,
            String name,
            String description,
            Integer duration,
            Integer price,
            Boolean offer,
            Integer specialPrice
    ) {
        validateInputs(barberiaId, name, description, duration, price, offer, specialPrice);

        Integer validatedSpecialPrice = validateOffer(price, offer, specialPrice);

        return new Service(
                id,
                barberiaId,
                name,
                description,
                duration,
                true,
                offer,
                price,
                validatedSpecialPrice,
                Instant.now(),
                null
        );
    }

    public static Service update(
            Integer id,
            UUID barberiaId,
            String name,
            String description,
            Integer duration,
            Boolean status,
            Boolean offer,
            Integer price,
            Integer specialPrice,
            Instant createAt
    ) {
        if (id == null || createAt == null) {
            throw new BusinessExceptions(ServiceMessageExceptions.INVALID_DATA);
        }

        validateInputs(barberiaId, name, description, duration, price, offer, specialPrice);

        Integer validatedSpecialPrice = validateOffer(price, offer, specialPrice);

        return new Service(
                id,
                barberiaId,
                name,
                description,
                duration,
                status,
                offer,
                price,
                validatedSpecialPrice,
                createAt,
                Instant.now()
        );
    }

    public static Service rebuild(
            Integer id,
            UUID barberiaId,
            String name,
            String description,
            Integer duration,
            Boolean status,
            Boolean offer,
            Integer price,
            Integer specialPrice,
            Instant createAt,
            Instant updateAt
    ) {
        if (id == null || createAt == null) {
            throw new BusinessExceptions(ServiceMessageExceptions.INVALID_DATA);
        }

        validateInputs(barberiaId, name, description, duration, price, offer, specialPrice);

        Integer validatedSpecialPrice = validateOffer(price, offer, specialPrice);

        return new Service(
                id,
                barberiaId,
                name,
                description,
                duration,
                status,
                offer,
                price,
                validatedSpecialPrice,
                createAt,
                updateAt
        );
    }

    private static void validateInputs(
            UUID barberiaId,
            String name,
            String description,
            Integer duration,
            Integer price,
            Boolean offer,
            Integer specialPrice
    ) {
        boolean invalid = barberiaId == null
                || isNullOrBlank(name)
                || isNullOrBlank(description)
                || duration == null
                || duration <= 0
                || price == null
                || price < 0
                || offer == null;

        if (invalid) {
            throw new BusinessExceptions(ServiceMessageExceptions.INVALID_DATA);
        }
    }

    private static Integer validateOffer(
            Integer price,
            Boolean offer,
            Integer specialPrice
    ) {
        if (Boolean.TRUE.equals(offer)) {
            if (specialPrice == null || specialPrice < 0) {
                throw new BusinessExceptions(ServiceMessageExceptions.INVALID_DATA);
            }
            if (specialPrice >= price) {
                throw new BusinessExceptions(ServiceMessageExceptions.OFFER_NOT_LOWER);
            }

            return specialPrice;
        }

        return null;
    }

    private static boolean isNullOrBlank(String value) {
        return value == null || value.isBlank();
    }

        /** Activa o desactiva el servicio; un servicio inactivo no se ofrece a los clientes. */
    public Service withStatus(boolean active) {
        return new Service(id, barberiaId, name, description, duration, active, offer, price,
                specialPrice, createAt, Instant.now());
    }

    public boolean isActive() {
        return !Boolean.FALSE.equals(status);
    }

    public Integer getId() { return id; }
    public UUID getBarberiaId() { return barberiaId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Integer getDuration() { return duration; }
    public Boolean getStatus() { return status; }
    public Boolean getOffer() { return offer; }
    public Integer getPrice() { return price; }
    public Integer getSpecialPrice() { return specialPrice; }

    /** Precio que se cobra hoy: el de oferta si el servicio está en oferta. */
    public Integer effectivePrice() {
        return Boolean.TRUE.equals(offer) && specialPrice != null ? specialPrice : price;
    }
    public Instant getCreateAt() { return createAt; }
    public Instant getUpdateAt() { return updateAt; }
}