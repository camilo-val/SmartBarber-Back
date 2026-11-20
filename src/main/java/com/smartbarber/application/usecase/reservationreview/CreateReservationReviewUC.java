package com.smartbarber.application.usecase.reservationreview;

import com.smartbarber.application.command.in.reservationreview.ReservationReviewCommand;
import com.smartbarber.application.usecase.security.AuthorizationAccessRules;
import com.smartbarber.domain.enums.ReservationStatus;
import com.smartbarber.domain.exceptions.BusinessExceptions;
import com.smartbarber.domain.exceptions.reservationreview.ReservationReviewMessageExceptions;
import com.smartbarber.domain.model.reservationreview.ReservationReview;
import com.smartbarber.domain.port.ReservationReviewPort;
import com.smartbarber.domain.port.authentication.CurrentAuthenticationPort;
import com.smartbarber.domain.port.reservation.ReservationRepositoryPort;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Reseña de una reserva. Solo el cliente de la reserva puede calificarla, una
 * sola vez y únicamente cuando la reserva ya fue finalizada (RW_005).
 */
@Component
@AllArgsConstructor
public class CreateReservationReviewUC {

    private final ReservationReviewPort reservationReviewPort;
    private final ReservationRepositoryPort reservationRepositoryPort;
    private final CurrentAuthenticationPort currentAuthenticationPort;

    public Mono<ReservationReview> createReservationReview(
            ReservationReviewCommand command
    ) {
        return Mono.fromCallable(() ->
                ReservationReview.createReservationReview(
                        null,
                        command.reservationId(),
                        command.qualification(),
                        command.comment()
                )
        ).flatMap(review -> ensureReviewable(review)
                .then(reservationReviewPort.existsByReservationId(review.getReservationId()))
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return Mono.error(
                                new BusinessExceptions(
                                        ReservationReviewMessageExceptions.RESERVATION_REVIEW_ALREADY_EXISTS
                                )
                        );
                    }
                    return reservationReviewPort.save(review);
                })
        );
    }

    private Mono<Void> ensureReviewable(ReservationReview review) {
        return Mono.zip(
                        currentAuthenticationPort.getCurrentAuthentication(),
                        reservationRepositoryPort.findById(review.getReservationId())
                )
                .switchIfEmpty(Mono.error(() -> notReviewable()))
                .flatMap(data -> {
                    AuthorizationAccessRules.validateCustomerScopedAccess(
                            data.getT1(), data.getT2().getCustomerId());
                    return data.getT2().getStatus() == ReservationStatus.COMPLETED
                            ? Mono.<Void>empty()
                            : Mono.error(notReviewable());
                });
    }

    private static BusinessExceptions notReviewable() {
        return new BusinessExceptions(ReservationReviewMessageExceptions.RESERVATION_NOT_REVIEWABLE);
    }
}
