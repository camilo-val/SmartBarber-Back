package com.smartbarber.application.usecase.security;

import com.smartbarber.domain.enums.RoleType;
import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.user.UserMessageExceptions;
import com.smartbarber.domain.model.authentication.Authentication;
import com.smartbarber.domain.model.customer.Customer;
import com.smartbarber.domain.model.employee.Employee;
import com.smartbarber.domain.model.reservation.Reservation;

import java.util.UUID;

public final class AuthorizationAccessRules {

    private AuthorizationAccessRules() {
    }

    public static void validateEmployeeScopedAccess(
            Authentication actualUser,
            Employee employee
    ) {
        boolean hasPermission =
                (isAdmin(actualUser)
                        && hasAuthenticatedEmployee(actualUser)
                        && actualUser.getEmployee().getBarberiaId().equals(employee.getBarberiaId()))
                        ||
                        (isBarber(actualUser)
                                && hasAuthenticatedEmployee(actualUser)
                                && actualUser.getEmployee().getId().equals(employee.getId()));

        if (!hasPermission) {
            deny();
        }
    }

    public static void validateAdminInBarbershop(
            Authentication actualUser,
            UUID barberiaId
    ) {
        boolean hasPermission =
                isAdmin(actualUser)
                        && hasAuthenticatedEmployee(actualUser)
                        && actualUser.getEmployee().getBarberiaId().equals(barberiaId);

        if (!hasPermission) {
            deny();
        }
    }

    public static void validateEmployeeTenantAccess(
            Authentication actualUser,
            UUID barberiaId
    ) {
        boolean hasPermission =
                (isAdmin(actualUser) || isBarber(actualUser))
                        && hasAuthenticatedEmployee(actualUser)
                        && actualUser.getEmployee().getBarberiaId().equals(barberiaId);

        if (!hasPermission) {
            deny();
        }
    }

    public static void validateCustomerScopedAccess(
            Authentication actualUser,
            Customer customer
    ) {
        validateCustomerScopedAccess(actualUser, customer.getId());
    }

    public static void validateCustomerScopedAccess(
            Authentication actualUser,
            UUID customerId
    ) {
        boolean hasPermission =
                isCustomer(actualUser)
                        && hasAuthenticatedCustomer(actualUser)
                        && actualUser.getCustomer().getId().equals(customerId);

        if (!hasPermission) {
            deny();
        }
    }

    public static void validateReservationAccess(
            Authentication actualUser,
            Reservation reservation
    ) {
        boolean hasPermission =
                (isAdmin(actualUser)
                        && hasAuthenticatedEmployee(actualUser)
                        && actualUser.getEmployee().getBarberiaId().equals(reservation.getBarbershopId()))
                        ||
                        (isBarber(actualUser)
                                && hasAuthenticatedEmployee(actualUser)
                                && actualUser.getEmployee().getId().equals(reservation.getEmployeeId()))
                        ||
                        (isCustomer(actualUser)
                                && hasAuthenticatedCustomer(actualUser)
                                && actualUser.getCustomer().getId().equals(reservation.getCustomerId()));

        if (!hasPermission) {
            deny();
        }
    }

    public static boolean isAdmin(Authentication actualUser) {
        return actualUser.getUser().getRoleId() == RoleType.ADMIN.getId();
    }

    public static boolean isBarber(Authentication actualUser) {
        return actualUser.getUser().getRoleId() == RoleType.BARBER.getId();
    }

    public static boolean isCustomer(Authentication actualUser) {
        return actualUser.getUser().getRoleId() == RoleType.CUSTOMER.getId();
    }

    private static boolean hasAuthenticatedEmployee(Authentication actualUser) {
        return actualUser.getEmployee() != null;
    }

    private static boolean hasAuthenticatedCustomer(Authentication actualUser) {
        return actualUser.getCustomer() != null;
    }

    private static void deny() {
        throw new BusinessExceptions(
                UserMessageExceptions.THE_USER_DOES_NOT_HAVE_PERMISSION
        );
    }
}
