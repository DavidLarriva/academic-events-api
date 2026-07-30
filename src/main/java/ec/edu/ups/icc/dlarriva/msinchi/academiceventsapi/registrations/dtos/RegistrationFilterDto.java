package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.dtos;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.enums.RegistrationStatus;

/**
 * Filtros de query params (contexto-materia.md sección 10.3). eventId/status
 * aplican para cualquier rol; participantId solo tiene efecto real para
 * ADMIN (el service fuerza participantId=currentUser.id para PARTICIPANT y
 * lo ignora para ORGANIZER, que en cambio queda scoped por los eventos que
 * organiza) — ver RegistrationServiceImpl#searchScopedByRole.
 */
public class RegistrationFilterDto {

    private Long eventId;

    private Long participantId;

    private RegistrationStatus status;

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public Long getParticipantId() {
        return participantId;
    }

    public void setParticipantId(Long participantId) {
        this.participantId = participantId;
    }

    public RegistrationStatus getStatus() {
        return status;
    }

    public void setStatus(RegistrationStatus status) {
        this.status = status;
    }
}
