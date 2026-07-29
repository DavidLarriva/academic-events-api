package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.sessions.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/**
 * Sin eventId: la sesión siempre se crea anidada bajo /events/{eventId}/sessions,
 * igual que organizerId en CreateEventDto nunca sale del body (acá el "dueño"
 * del contexto es la URL, no el cliente).
 */
public class CreateSessionDto {

    @Schema(description = "Único junto con startAt dentro del mismo evento", example = "Configuración de Spring Security")
    @NotBlank
    @Size(max = 160)
    private String title;

    @Schema(description = "Descripción de la sesión")
    private String description;

    @Schema(description = "Debe caer dentro de [event.startAt, event.endAt]", example = "2026-08-20T15:00:00Z")
    @NotNull
    private OffsetDateTime startAt;

    @Schema(description = "Posterior a startAt, dentro del rango del evento", example = "2026-08-20T16:30:00Z")
    @NotNull
    private OffsetDateTime endAt;

    @Schema(description = "Lugar físico, si aplica", example = "Laboratorio de Computación 3")
    @Size(max = 200)
    private String location;

    @Schema(description = "Enlace virtual, si aplica")
    @Size(max = 500)
    private String virtualUrl;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public OffsetDateTime getStartAt() {
        return startAt;
    }

    public void setStartAt(OffsetDateTime startAt) {
        this.startAt = startAt;
    }

    public OffsetDateTime getEndAt() {
        return endAt;
    }

    public void setEndAt(OffsetDateTime endAt) {
        this.endAt = endAt;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getVirtualUrl() {
        return virtualUrl;
    }

    public void setVirtualUrl(String virtualUrl) {
        this.virtualUrl = virtualUrl;
    }
}
