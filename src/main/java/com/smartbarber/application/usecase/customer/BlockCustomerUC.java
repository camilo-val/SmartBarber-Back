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
public class BlockCustomerUC {
    private final CustomerPort customerPort;
    private final UserPort  userPort;
    private final CurrentAuthenticationPort currentAuthenticationPort;

    public Mono<Void> blockCustomer(UUID customerId) {
        return Mono.zip(
                currentAuthenticationPort.getCurrentAuthentication(),
                customerPort.findById(customerId)
                        .switchIfEmpty(Mono.error(() -> new BusinessExceptions(CustomerMessageExceptions.INVALID_CUSTOMER)))
        ).flatMap(tuple -> {
                    validateOwner(tuple.getT1(), tuple.getT2());
                    return userPort.findById(tuple.getT2().getUserId())
                            .flatMap(user ->
                                    userPort.save(user.blockUSer(user.getId()))
                            );
                }
        )
        .then();
    }

    private void validateOwner(Authentication actualUser, Customer customer) {

        boolean hasPermission =
                (actualUser.getUser().getRoleId() == RoleType.CUSTOMER.getId()
                        && actualUser.getCustomer().getId().equals(customer.getId()));

        if (!hasPermission) {
            throw new BusinessExceptions(UserMessageExceptions.THE_USER_DOES_NOT_HAVE_PERMISSION);
        }

    }

}
