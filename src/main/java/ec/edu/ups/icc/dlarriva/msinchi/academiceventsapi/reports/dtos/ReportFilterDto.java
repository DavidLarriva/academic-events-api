package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.reports.dtos;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.enums.RegistrationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * Filtros del reporte de inscripciones (docs/instrucciones.pdf sección 13). status
 * arranca en CONFIRMED (decisión acordada con el usuario: un "listado de
 * inscritos" es el roster de asistentes confirmados, no las 4 estados
 * mezclados) — para ver otro estado se pasa ?status=PENDING explícitamente.
 * from/to acotan por fecha de inscripción (registeredAt), no por fecha del
 * evento.
 */
public class ReportFilterDto {

    @Schema(description = "Fecha de inscripción desde (UTC)", example = "2026-01-01T00:00:00Z")
    private OffsetDateTime from;

    @Schema(description = "Fecha de inscripción hasta (UTC)", example = "2026-12-31T23:59:59Z")
    private OffsetDateTime to;

    @Schema(description = "Estado a incluir en el reporte", example = "CONFIRMED")
    private RegistrationStatus status = RegistrationStatus.CONFIRMED;

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

    public RegistrationStatus getStatus() {
        return status;
    }

    public void setStatus(RegistrationStatus status) {
        this.status = status;
    }
}
