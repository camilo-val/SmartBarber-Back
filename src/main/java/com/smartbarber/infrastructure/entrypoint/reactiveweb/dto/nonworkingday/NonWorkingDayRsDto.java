package com.smartbarber.infrastructure.entrypoint.reactiveweb.dto.nonworkingday;

import com.smartbarber.domain.model.nonworkingday.NonWorkingDay;
import lombok.Builder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Builder
public record NonWorkingDayRsDto(
        UUID id,
        UUID barbershopId,
        LocalDate date,
        String reason,
        Instant createdAt
) {
    public static NonWorkingDayRsDto from(NonWorkingDay domain) {
        return NonWorkingDayRsDto.builder()
                .id(domain.getId())
                .barbershopId(domain.getBarbershopId())
                .date(domain.getDate())
                .reason(domain.getReason())
                .createdAt(domain.getCreatedAt())
                .build();
    }
}
