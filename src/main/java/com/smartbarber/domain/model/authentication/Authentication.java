package com.smartbarber.domain.model.authentication;

import com.smartbarber.domain.model.barbershop.BarbershopResponse;
import com.smartbarber.domain.model.customer.Customer;
import com.smartbarber.domain.model.employee.Employee;
import com.smartbarber.domain.model.user.User;


public class Authentication {
    private final BarbershopResponse barbershop;
    private final Employee employee;
    private final Customer customer;
    private final User user;

    public Authentication(BarbershopResponse barbershop, Employee employee, Customer customer, User user) {
        this.barbershop = barbershop;
        this.employee = employee;
        this.customer = customer;
        this.user = user;
    }

    public static Authentication mapCustomer(Customer customer, User user){
        return new Authentication(null, null, customer, user);
    }

    public static Authentication mapEmployee(BarbershopResponse barbershop, Employee employee, User user){
        return new Authentication(barbershop, employee, null, user);
    }

    public BarbershopResponse getBarbershop() {
        return barbershop;
    }

    public Employee getEmployee() {
        return employee;
    }

    public Customer getCustomer() {
        return customer;
    }

    public User getUser() {
        return user;
    }

    @Override
    public String toString() {
        return "Authentication{" +
                "barbershop=" + barbershop +
                ", employee=" + employee +
                ", customer=" + customer +
                ", user=" + user +
                '}';
    }
}
