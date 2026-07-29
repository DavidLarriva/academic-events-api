package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.dtos;

import jakarta.validation.constraints.NotNull;

/**
 * Sin participantId: el dueño de la inscripción sale siempre de
 * @AuthenticationPrincipal, nunca del body (contexto-materia.md §14.3).
 */
public class CreateRegistrationDto {

    @NotNull
    private Long eventId;

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }
}
