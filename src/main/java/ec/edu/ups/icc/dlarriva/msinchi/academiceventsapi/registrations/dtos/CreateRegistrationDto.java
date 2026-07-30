package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Sin participantId: el dueño de la inscripción sale siempre de
 * @AuthenticationPrincipal, nunca del body (contexto-materia.md sección 14.3).
 */
public class CreateRegistrationDto {

    @Schema(description = "Evento PUBLISHED con cupo disponible", example = "1")
    @NotNull
    private Long eventId;

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }
}
