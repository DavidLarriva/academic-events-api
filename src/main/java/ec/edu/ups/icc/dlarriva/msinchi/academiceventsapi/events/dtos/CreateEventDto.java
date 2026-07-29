package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.dtos;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.enums.EventModality;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/**
 * Sin "status" (siempre nace DRAFT) ni "availableCapacity" (nace igual a
 * capacity: todavía no hay inscripciones que descuenten, ver prompt de
 * registrations). El organizerId tampoco está acá: sale siempre de
 * @AuthenticationPrincipal, nunca del body.
 */
public class CreateEventDto {

    @Schema(description = "Título público del evento", example = "Taller de seguridad con Spring Boot")
    @NotBlank
    @Size(max = 160)
    private String title;

    @Schema(description = "Descripción detallada", example = "Construcción de una API REST segura con JWT y roles.")
    @NotBlank
    private String description;

    @Schema(description = "Modalidad: define qué combinación de location/virtualUrl es válida")
    @NotNull
    private EventModality modality;

    @Schema(description = "Requerido si PRESENTIAL/HYBRID, prohibido si VIRTUAL", example = "Auditorio Principal")
    @Size(max = 200)
    private String location;

    @Schema(description = "Requerido si VIRTUAL/HYBRID, prohibido si PRESENTIAL",
            example = "https://meet.example.test/spring-security")
    @Size(max = 500)
    private String virtualUrl;

    @Schema(description = "Cupo total; available_capacity nace igual a este valor", example = "40")
    @NotNull
    @Min(1)
    private Integer capacity;

    @Schema(description = "Inicio del periodo de inscripciones (UTC)", example = "2026-08-01T00:00:00Z")
    @NotNull
    private OffsetDateTime registrationStartAt;

    @Schema(description = "Fin del periodo de inscripciones (UTC), <= startAt", example = "2026-08-15T23:59:59Z")
    @NotNull
    private OffsetDateTime registrationEndAt;

    @Schema(description = "Inicio del evento (UTC)", example = "2026-08-20T15:00:00Z")
    @NotNull
    private OffsetDateTime startAt;

    @Schema(description = "Fin del evento (UTC), posterior a startAt", example = "2026-08-20T19:00:00Z")
    @NotNull
    private OffsetDateTime endAt;

    @Schema(description = "Id de una categoría activa", example = "1")
    @NotNull
    private Long categoryId;

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

    public EventModality getModality() {
        return modality;
    }

    public void setModality(EventModality modality) {
        this.modality = modality;
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

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public OffsetDateTime getRegistrationStartAt() {
        return registrationStartAt;
    }

    public void setRegistrationStartAt(OffsetDateTime registrationStartAt) {
        this.registrationStartAt = registrationStartAt;
    }

    public OffsetDateTime getRegistrationEndAt() {
        return registrationEndAt;
    }

    public void setRegistrationEndAt(OffsetDateTime registrationEndAt) {
        this.registrationEndAt = registrationEndAt;
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

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }
}
