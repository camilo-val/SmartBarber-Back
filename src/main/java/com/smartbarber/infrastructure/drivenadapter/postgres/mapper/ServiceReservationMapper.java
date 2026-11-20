package com.smartbarber.infrastructure.drivenadapter.postgres.mapper;

import com.smartbarber.domain.model.reservation.ServiceReservation;
import com.smartbarber.infrastructure.drivenadapter.postgres.entity.ServiceReservationEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ServiceReservationMapper {
    default ServiceReservation toDomain(ServiceReservationEntity entity){
        if (entity == null) {
            return null;
        }
        return ServiceReservation.rebuild(
                entity.getId(),
                entity.getReservationId(),
                entity.getServiceId(),
                entity.getStatus(),
                entity.getDuration(),
                entity.getPrice(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
    ServiceReservationEntity toEntity (ServiceReservation domain);
}
