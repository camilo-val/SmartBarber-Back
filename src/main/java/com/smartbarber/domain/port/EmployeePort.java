package com.smartbarber.domain.port;

import com.smartbarber.domain.model.employee.Employee;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface EmployeePort {
    Mono<Employee> findById(UUID id);
    Mono<Employee> findByName(String name);
    Mono<Employee> findByDocument(String document);
    Flux <Employee> findAllBarberId(UUID barberId);
    Mono<Employee> findByUserId(UUID userId);
    Mono<Employee> save(Employee employee);
    Mono<Employee> update( UUID id, Employee employee);
    Mono<Void> eliminarEmpleado(UUID id);
    Mono<Boolean> existsByDocument(String document);
    Mono<Employee> findAdministratorByBarbershopId(UUID barbershopId);


}
