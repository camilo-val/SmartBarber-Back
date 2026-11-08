package com.smartbarber.application.usecase.athentication;

import com.smartbarber.domain.enums.RoleType;
import com.smartbarber.domain.enums.SubscriptionBarberStatus;
import com.smartbarber.domain.enums.UserStatus;
import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.authorization.AuthorizationMessageExceptions;
import com.smartbarber.domain.model.authentication.Authentication;
import com.smartbarber.domain.model.barbershop.Barbershop;
import com.smartbarber.domain.model.barbershop.BarbershopResponse;
import com.smartbarber.domain.model.role.Role;
import com.smartbarber.domain.model.subscriptionbarber.SubscriptionBarbershop;
import com.smartbarber.domain.model.user.User;
import com.smartbarber.domain.port.*;
import com.smartbarber.domain.port.subscriptionbarbershop.SubscriptionBarbershopRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@Component
@Log4j2
public class
AuthenticationUC {
    private final FirebaseAuthPort firebaseAuthPort;
    private final UserPort userPort;
    private final BarberShopRepositoryPort barberShopRepositoryPort;
    private final EmployeePort employeePort;
    private final CustomerPort customerPort;
    private final RolePort rolePort;
    private final SubscriptionBarbershopRepositoryPort subscriptionRepositoryPort;

    public Mono<RoleType> authorization(String idToken){
        System.out.println("Authentication.authorization: " + idToken);
        return firebaseAuthPort.isValidToken(idToken)
                .flatMap(isValid ->{
                    if (Boolean.FALSE.equals(isValid)){
                        return Mono.error(()->new BusinessExceptions(AuthorizationMessageExceptions.THE_TOKEN_IS_EXPIRED));
                    }
                            return Mono.zip(firebaseAuthPort.isExpiredToken(idToken),firebaseAuthPort.getUid(idToken) )
                                    .map(tuple -> {
                                        Boolean isExpiredToken = tuple.getT1();
                                        String uidUser = tuple.getT2();
                                        if(Boolean.FALSE.equals(isExpiredToken)){
                                            return uidUser;
                                        }
                                        throw new  BusinessExceptions(AuthorizationMessageExceptions.THE_TOKEN_IS_EXPIRED);
                                    }).flatMap(userPort::findByFirebaseId)
                                    .flatMap(user ->
                                        !user.getStatus().equals(UserStatus.ACTIVE)
                                                ? Mono.error(()->new BusinessExceptions(AuthorizationMessageExceptions.THE_USER_IS_NOT_ACTIVE))
                                                : rolePort.findById(user.getRoleId())
                                    )
                                    .doOnNext(role -> log.info("role: {}" ,role))
                                    .map(Role::getRoleType)
                                    .doOnNext(roleType -> log.info("roleType: {}" ,roleType));
                        }
                    );
    }

    public Mono<Authentication> authorizationSession (String idToken){
        return authorization(idToken)
                .doOnNext(roleType -> log.info("roleTypeSSS: {}" ,roleType))
                .map(roleType -> roleType == RoleType.CUSTOMER)
                .flatMap(isCustomer -> getUserUid(idToken)
                        .flatMap(userPort::findByFirebaseId)
                        .flatMap(user -> Boolean.TRUE.equals(isCustomer)
                                    ? buildCustomerResponse(user)
                                    : buildEmployeeResponse(user)
                        )

                ).switchIfEmpty(Mono.error(()->new BusinessExceptions(AuthorizationMessageExceptions.INVALID_DATA)))
                .doOnError(error -> new BusinessExceptions(AuthorizationMessageExceptions.FORBIDDEN));
    }
    public Mono<String> getUserUid(String idToken){
        System.out.println("Authentication.getUserUid: " + idToken);
        return firebaseAuthPort.isValidToken(idToken).
                flatMap(mapClaims -> firebaseAuthPort.getUid(idToken));
    }

    private BarbershopResponse buildBarber(Barbershop barbershop, SubscriptionBarbershop subscription){
        return BarbershopResponse.builder().
                id(barbershop.getId()).
                name(barbershop.getName()).
                location(barbershop.getLocation()).
                phone(barbershop.getPhone()).
                companyName(barbershop.getCompanyName()).
                subscription(subscription).
                build();
    }

    private Mono<Authentication> buildCustomerResponse(User user){
        return customerPort.findByUserId(user.getId())
                .doOnNext(customer -> log.info("customer: {}" ,customer))
                .map(customer -> Authentication.mapCustomer(customer,user));
    }

    private Mono<Authentication> buildEmployeeResponse(User user){
        return employeePort.findByUserId(user.getId())
                .flatMap(employee -> {
                    return Mono.zip(
                                    barberShopRepositoryPort.findById(employee.getBarberiaId()),
                                    subscriptionRepositoryPort.findByBarberIdAndStatus(employee.getBarberiaId(), SubscriptionBarberStatus.ACTIVE)
                                            .defaultIfEmpty(SubscriptionBarbershop.getSubscription(SubscriptionBarberStatus.NOT_SUBSCRIPTION)))
                            .map(tuple -> {
                                Barbershop barbershop =  tuple.getT1();
                                SubscriptionBarbershop subscriptionBarber = SubscriptionBarbershop.getSubscription(tuple.getT2().getStatus());
                                return Authentication.mapEmployee(buildBarber(barbershop,subscriptionBarber ),employee,user);

                            });
                });
    }
}
