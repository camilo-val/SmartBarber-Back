package com.smartbarber.application.usecase.nonworkingday;

import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.nonworkingday.NonWorkingDayMessageExceptions;
import com.smartbarber.domain.port.NonWorkingDayPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DeleteNonWorkingDayUC {

    private final NonWorkingDayPort nonWorkingDayPort;
    private final BarbershopAdminResolver adminResolver;

    public Mono<Void> delete(UUID id) {
        return adminResolver.currentAdmin()
                .flatMap(admin -> nonWorkingDayPort.findById(id)
                        .switchIfEmpty(Mono.error(() -> new BusinessExceptions(NonWorkingDayMessageExceptions.NON_WORKING_DAY_NOT_FOUND)))
                        .flatMap(day -> day.belongsTo(admin.barbershopId())
                                ? nonWorkingDayPort.deleteById(day.getId())
                                // Para otra barbería se responde igual que si no existiera.
                                : Mono.error(new BusinessExceptions(NonWorkingDayMessageExceptions.NON_WORKING_DAY_NOT_FOUND))));
    }
}
