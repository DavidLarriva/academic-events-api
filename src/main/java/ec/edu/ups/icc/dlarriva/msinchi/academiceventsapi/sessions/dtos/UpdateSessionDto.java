package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.sessions.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/**
 * Reemplazo total (PUT). No permite mover la sesión a otro evento: el
 * eventId de la URL es fijo, igual que en la creación.
 */
public class UpdateSessionDto {

    @Schema(description = "Único junto con startAt dentro del mismo evento")
    @NotBlank
    @Size(max = 160)
    private String title;

    @Schema(description = "Descripción de la sesión")
    private String description;

    @Schema(description = "Debe caer dentro de [event.startAt, event.endAt]")
    @NotNull
    private OffsetDateTime startAt;

    @Schema(description = "Posterior a startAt, dentro del rango del evento")
    @NotNull
    private OffsetDateTime endAt;

    @Schema(description = "Lugar físico, si aplica")
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
