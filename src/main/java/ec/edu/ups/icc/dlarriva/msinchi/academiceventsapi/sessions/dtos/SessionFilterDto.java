package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.sessions.dtos;

import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/**
 * Filtros de query params para el listado de sesiones de UN evento
 * (contexto-materia.md §10.3). eventId no está acá: llega por la URL
 * (/events/{eventId}/sessions), no como filtro.
 */
public class SessionFilterDto {

    @Size(max = 160)
    private String title;

    private OffsetDateTime startFrom;

    private OffsetDateTime startTo;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public OffsetDateTime getStartFrom() {
        return startFrom;
    }

    public void setStartFrom(OffsetDateTime startFrom) {
        this.startFrom = startFrom;
    }

    public OffsetDateTime getStartTo() {
        return startTo;
    }

    public void setStartTo(OffsetDateTime startTo) {
        this.startTo = startTo;
    }
}
