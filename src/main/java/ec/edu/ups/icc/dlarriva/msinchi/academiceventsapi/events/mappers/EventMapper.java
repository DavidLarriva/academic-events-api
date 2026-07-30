package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.mappers;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.dtos.EventResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.entities.EventEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.models.EventModel;

/**
 * Conversión manual, sin MapStruct (contexto-materia.md sección 4.4).
 */
public final class EventMapper {

    private EventMapper() {
    }

    public static EventModel toModel(EventEntity entity) {
        EventModel model = new EventModel();
        model.setId(entity.getId());
        model.setTitle(entity.getTitle());
        model.setDescription(entity.getDescription());
        model.setModality(entity.getModality());
        model.setLocation(entity.getLocation());
        model.setVirtualUrl(entity.getVirtualUrl());
        model.setCapacity(entity.getCapacity());
        model.setAvailableCapacity(entity.getAvailableCapacity());
        model.setRegistrationStartAt(entity.getRegistrationStartAt());
        model.setRegistrationEndAt(entity.getRegistrationEndAt());
        model.setStartAt(entity.getStartAt());
        model.setEndAt(entity.getEndAt());
        model.setStatus(entity.getStatus());
        // .getId() sobre una relación LAZY no dispara carga: el identificador
        // ya está en el proxy sin necesidad de tocar la base de datos.
        model.setOrganizerId(entity.getOrganizer().getId());
        model.setCategoryId(entity.getCategory().getId());
        model.setDeleted(entity.isDeleted());
        model.setVersion(entity.getVersion());
        model.setCreatedAt(entity.getCreatedAt());
        model.setUpdatedAt(entity.getUpdatedAt());
        return model;
    }

    public static EventResponseDto toResponse(EventModel model) {
        return new EventResponseDto(
                model.getId(), model.getTitle(), model.getDescription(), model.getModality(),
                model.getLocation(), model.getVirtualUrl(), model.getCapacity(), model.getAvailableCapacity(),
                model.getRegistrationStartAt(), model.getRegistrationEndAt(), model.getStartAt(), model.getEndAt(),
                model.getStatus(), model.getOrganizerId(), model.getCategoryId(), model.getVersion(),
                model.getCreatedAt(), model.getUpdatedAt());
    }
}
