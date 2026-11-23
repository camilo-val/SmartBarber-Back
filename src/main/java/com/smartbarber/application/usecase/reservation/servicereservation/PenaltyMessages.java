package com.smartbarber.application.usecase.reservation.servicereservation;

import com.smartbarber.application.usecase.penalty.CustomerPenaltyUC;
import com.smartbarber.application.usecase.penalty.PenaltyNotifier;
import com.smartbarber.domain.model.penalty.PenaltyPolicy;
import com.smartbarber.domain.model.penalty.PenaltyStanding;

/**
 * Textos del aviso al cliente cuando se registra una inasistencia
 * ("Qué ve cada rol" de la política de penalización).
 */
final class PenaltyMessages {

    private PenaltyMessages() {
    }

    static String noShowTitle(CustomerPenaltyUC.NoShowOutcome outcome) {
        PenaltyStanding standing = outcome.view().standing();
        String shop = outcome.view().barbershopName();
        if (outcome.restrictionCreated()) {
            return "No podrás reservar en " + shop + " por un tiempo";
        }
        if (standing.state() == PenaltyStanding.State.WARNED) {
            return "Aviso: una inasistencia más y no podrás reservar en " + shop;
        }
        return "Registramos una inasistencia en " + shop;
    }

    static String noShowBody(CustomerPenaltyUC.NoShowOutcome outcome) {
        PenaltyStanding standing = outcome.view().standing();
        PenaltyPolicy policy = outcome.view().policy();
        String shop = outcome.view().barbershopName();
        String registered = "Registramos que no asististe a tu cita en " + shop + ".";

        if (!standing.policyEnabled()) {
            return registered + "\n\nSi no puedes asistir a una cita, cancélala con anticipación "
                    + "para que otro cliente pueda usar ese espacio.";
        }

        String counter = "Llevas " + standing.noShowCount() + " de " + standing.threshold()
                + " inasistencias en los últimos " + policy.getCountingPeriodDays() + " días.";

        if (outcome.restrictionCreated()) {
            return registered + " " + counter + "\n\n"
                    + "Por eso no podrás crear nuevas reservas en " + shop + " hasta el "
                    + PenaltyNotifier.formatDate(standing.activeRestriction().getEndsAt()) + ". "
                    + "Tus reservas actuales se mantienen y puedes seguir usando la cola virtual, "
                    + "donde se atiende por orden de llegada.\n\nEsta medida no tiene ningún cobro.";
        }
        if (standing.isRestricted()) {
            return registered + "\n\nTu restricción actual se mantiene hasta el "
                    + PenaltyNotifier.formatDate(standing.activeRestriction().getEndsAt())
                    + " y no se alarga por esta inasistencia.";
        }
        if (standing.state() == PenaltyStanding.State.WARNED) {
            return registered + " " + counter + "\n\n"
                    + "Una inasistencia más y no podrás reservar en " + shop + " durante "
                    + policy.getRestrictionDays() + " días. Si no puedes asistir, cancela hasta "
                    + policy.getCancelNoticeHours() + " horas antes: así no cuenta como inasistencia.";
        }
        return registered + " " + counter + "\n\n"
                + "Si no puedes asistir a una cita, cancela hasta " + policy.getCancelNoticeHours()
                + " horas antes: así no cuenta como inasistencia.";
    }
}
