package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.dtos;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.enums.RegistrationStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Un único endpoint (PATCH /registrations/{id}/status) cubre las 3
 * transiciones posibles (CONFIRMED/REJECTED por el ORGANIZER dueño del
 * evento o ADMIN; CANCELLED por el PARTICIPANT dueño o ADMIN) — el service
 * decide quién puede pedir cada status según OwnershipValidator.
 */
public class UpdateRegistrationStatusDto {

    @NotNull
    private RegistrationStatus status;

    public RegistrationStatus getStatus() {
        return status;
    }

    public void setStatus(RegistrationStatus status) {
        this.status = status;
    }
}
