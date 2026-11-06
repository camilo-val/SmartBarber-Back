package com.smartbarber.infrastructure.entrypoint.helper;

import com.smartbarber.domain.model.authentication.Authentication;
import com.smartbarber.domain.model.customer.Customer;
import com.smartbarber.domain.model.employee.Employee;
import com.smartbarber.domain.port.authentication.CurrentAuthenticationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class SecurityFacade {

    private final CurrentAuthenticationPort currentAuthenticationPort;

    public Mono<Authentication> currentAuthentication() {
        return currentAuthenticationPort.getCurrentAuthentication();
    }

    public Mono<Employee> currentEmployee() {
        return currentAuthenticationPort.getCurrentAuthentication()
                .map(Authentication::getEmployee);
    }

    public Mono<Customer> currentCustomer() {
        return currentAuthenticationPort.getCurrentAuthentication()
                .map(Authentication::getCustomer);
    }
}