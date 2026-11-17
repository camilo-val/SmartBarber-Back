package com.smartbarber.application.usecase.customer;

import com.smartbarber.domain.enums.RoleType;
import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.customer.CustomerMessageExceptions;
import com.smartbarber.domain.exceptions.user.UserMessageExceptions;
import com.smartbarber.domain.model.authentication.Authentication;
import com.smartbarber.domain.model.customer.Customer;
import com.smartbarber.domain.port.CustomerPort;
import com.smartbarber.domain.port.UserPort;
import com.smartbarber.domain.port.authentication.CurrentAuthenticationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UnblockCustomerUC {

    private final CustomerPort customerPort;
    private final UserPort userPort;
    private final CurrentAuthenticationPort currentAuthenticationPort;

    public Mono<Void> unblockCustomer(UUID customerId) {

        return Mono.zip(
                currentAuthenticationPort.getCurrentAuthentication(),
                customerPort.findById(customerId)
                        .switchIfEmpty(Mono.error(
                                () -> new BusinessExceptions(
                                        CustomerMessageExceptions.INVALID_CUSTOMER
                                )
                        ))
        ).flatMap(tuple -> {

            System.out.println("===== UNBLOCK CUSTOMER =====");

            System.out.println(
                    "Usuario autenticado: "
                            + tuple.getT1().getUser().getId()
            );

            System.out.println(
                    "Rol autenticado: "
                            + tuple.getT1().getUser().getRoleId()
            );

            System.out.println(
                    "Customer solicitado: "
                            + tuple.getT2().getId()
            );

            System.out.println(
                    "User asociado: "
                            + tuple.getT2().getUserId()
            );

            validateOwner(tuple.getT1(), tuple.getT2());

            return userPort.findById(tuple.getT2().getUserId())
                    .flatMap(user -> {

                        System.out.println(
                                "Estado antes: " + user.getStatus()
                        );

                        return userPort.save(user.unblockUser(user.getId()))
                                .doOnNext(updated ->
                                        System.out.println(
                                                "Estado después: "
                                                        + updated.getStatus()
                                        )
                                );
                    });
        }).then();
    }

    private void validateOwner(
            Authentication actualUser,
            Customer customer
    ) {

        boolean hasPermission =
                actualUser.getUser().getRoleId() == RoleType.CUSTOMER.getId()
                        && actualUser.getCustomer().getId().equals(customer.getId());

        if (!hasPermission) {
            throw new BusinessExceptions(
                    UserMessageExceptions.THE_USER_DOES_NOT_HAVE_PERMISSION
            );
        }
    }
}