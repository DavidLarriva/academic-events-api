package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.sessions.mappers;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.sessions.dtos.SessionResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.sessions.entities.SessionEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.sessions.models.SessionModel;

/**
 * Conversión manual, sin MapStruct (contexto-materia.md §4.4). No hay
 * toModel(CreateSessionDto)/toEntity(model) como en categories: crear una
 * sesión requiere adjuntar el EventEntity ya cargado (no solo un id), así
 * que ese armado vive en SessionServiceImpl, igual que EventServiceImpl.
 */
public final class SessionMapper {

    private SessionMapper() {
    }

    public static SessionModel toModel(SessionEntity entity) {
        SessionModel model = new SessionModel();
        model.setId(entity.getId());
        // .getId() sobre una relación LAZY no dispara carga: el identificador
        // ya está en el proxy sin necesidad de tocar la base de datos.
        model.setEventId(entity.getEvent().getId());
        model.setTitle(entity.getTitle());
        model.setDescription(entity.getDescription());
        model.setStartAt(entity.getStartAt());
        model.setEndAt(entity.getEndAt());
        model.setLocation(entity.getLocation());
        model.setVirtualUrl(entity.getVirtualUrl());
        model.setCreatedAt(entity.getCreatedAt());
        model.setUpdatedAt(entity.getUpdatedAt());
        return model;
    }

    public static SessionResponseDto toResponse(SessionModel model) {
        return new SessionResponseDto(
                model.getId(), model.getEventId(), model.getTitle(), model.getDescription(),
                model.getStartAt(), model.getEndAt(), model.getLocation(), model.getVirtualUrl(),
                model.getCreatedAt(), model.getUpdatedAt());
    }
}
