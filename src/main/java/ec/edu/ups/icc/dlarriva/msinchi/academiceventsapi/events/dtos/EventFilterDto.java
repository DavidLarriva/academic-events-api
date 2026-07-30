package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.dtos;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.enums.EventStatus;

import java.time.OffsetDateTime;

/**
 * Filtros de query params para el listado (contexto-materia.md sección 10.3):
 * categoría, estado, organizador y rango de fechas sobre startAt.
 */
public class EventFilterDto {

    private Long categoryId;
    private EventStatus status;
    private Long organizerId;
    private OffsetDateTime startFrom;
    private OffsetDateTime startTo;

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public EventStatus getStatus() {
        return status;
    }

    public void setStatus(EventStatus status) {
        this.status = status;
    }

    public Long getOrganizerId() {
        return organizerId;
    }

    public void setOrganizerId(Long organizerId) {
        this.organizerId = organizerId;
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
