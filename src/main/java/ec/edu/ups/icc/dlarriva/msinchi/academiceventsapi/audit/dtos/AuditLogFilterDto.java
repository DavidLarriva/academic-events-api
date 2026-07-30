package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.audit.dtos;

import java.time.OffsetDateTime;

/**
 * Filtros de query params (contexto-materia.md sección 10.3) para el listado de
 * solo lectura de ADMIN.
 */
public class AuditLogFilterDto {

    private Long actorId;

    private String action;

    private String resourceType;

    private OffsetDateTime from;

    private OffsetDateTime to;

    public Long getActorId() {
        return actorId;
    }

    public void setActorId(Long actorId) {
        this.actorId = actorId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public OffsetDateTime getFrom() {
        return from;
    }

    public void setFrom(OffsetDateTime from) {
        this.from = from;
    }

    public OffsetDateTime getTo() {
        return to;
    }

    public void setTo(OffsetDateTime to) {
        this.to = to;
    }
}
