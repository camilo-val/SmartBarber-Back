package com.smartbarber.application.usecase.nonworkingday;

import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.nonworkingday.NonWorkingDayMessageExceptions;
import com.smartbarber.domain.model.nonworkingday.NonWorkingDay;
import com.smartbarber.domain.port.NonWorkingDayPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class CreateNonWorkingDayUC {

    private final NonWorkingDayPort nonWorkingDayPort;
    private final BarbershopAdminResolver adminResolver;
    private final BarbershopCalendar calendar;

    public Mono<NonWorkingDay> create(LocalDate date, String reason) {
        return adminResolver.currentAdmin()
                .flatMap(admin -> {
                    NonWorkingDay nonWorkingDay = NonWorkingDay.create(
                            admin.barbershopId(), date, reason, admin.userId(), calendar.today());

                    return nonWorkingDayPort.existsByBarbershopAndDate(admin.barbershopId(), date)
                            .flatMap(exists -> Boolean.TRUE.equals(exists)
                                    ? Mono.error(new BusinessExceptions(NonWorkingDayMessageExceptions.NON_WORKING_DAY_ALREADY_EXISTS))
                                    : nonWorkingDayPort.save(nonWorkingDay));
                });
    }
}
