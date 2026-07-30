package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.mappers;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.dtos.RegistrationResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.entities.RegistrationEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.models.RegistrationModel;

/**
 * Conversión manual, sin MapStruct (contexto-materia.md sección 4.4).
 */
public final class RegistrationMapper {

    private RegistrationMapper() {
    }

    public static RegistrationModel toModel(RegistrationEntity entity) {
        RegistrationModel model = new RegistrationModel();
        model.setId(entity.getId());
        model.setRegistrationCode(entity.getRegistrationCode());
        // .getId() sobre una relación LAZY no dispara carga: el identificador
        // ya está en el proxy sin necesidad de tocar la base de datos.
        model.setEventId(entity.getEvent().getId());
        model.setParticipantId(entity.getParticipant().getId());
        model.setStatus(entity.getStatus());
        model.setRegisteredAt(entity.getRegisteredAt());
        model.setStatusUpdatedAt(entity.getStatusUpdatedAt());
        model.setConfirmedAt(entity.getConfirmedAt());
        model.setCancelledAt(entity.getCancelledAt());
        model.setVersion(entity.getVersion());
        return model;
    }

    public static RegistrationResponseDto toResponse(RegistrationModel model) {
        return new RegistrationResponseDto(
                model.getId(), model.getRegistrationCode(), model.getEventId(), model.getParticipantId(),
                model.getStatus(), model.getRegisteredAt(), model.getStatusUpdatedAt(), model.getConfirmedAt(),
                model.getCancelledAt(), model.getVersion());
    }
}
