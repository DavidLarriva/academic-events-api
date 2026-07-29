package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.dtos;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.enums.RegistrationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Un único endpoint (PATCH /registrations/{id}/status) cubre las 3
 * transiciones posibles (CONFIRMED/REJECTED por el ORGANIZER dueño del
 * evento o ADMIN; CANCELLED por el PARTICIPANT dueño o ADMIN) — el service
 * decide quién puede pedir cada status según OwnershipValidator.
 */
public class UpdateRegistrationStatusDto {

    @Schema(description = "Estado destino: CONFIRMED/REJECTED (organizer/admin) o CANCELLED (participante/admin)",
            example = "CONFIRMED")
    @NotNull
    private RegistrationStatus status;

    public RegistrationStatus getStatus() {
        return status;
    }

    public void setStatus(RegistrationStatus status) {
        this.status = status;
    }
}
