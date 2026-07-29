package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.audit.mappers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.audit.dtos.AuditLogResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.audit.entities.AuditLogEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.audit.models.AuditLogModel;

/**
 * Conversión manual, sin MapStruct (contexto-materia.md §4.4).
 */
public final class AuditLogMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private AuditLogMapper() {
    }

    public static AuditLogModel toModel(AuditLogEntity entity) {
        AuditLogModel model = new AuditLogModel();
        model.setId(entity.getId());
        // actor es nullable (LOGIN_FAILED con correo inexistente); cuando no
        // es null, .getId() sobre la relación LAZY no dispara carga.
        model.setActorId(entity.getActor() != null ? entity.getActor().getId() : null);
        model.setAction(entity.getAction());
        model.setResourceType(entity.getResourceType());
        model.setResourceId(entity.getResourceId());
        model.setPreviousValue(entity.getPreviousValue());
        model.setNewValue(entity.getNewValue());
        model.setResult(entity.getResult());
        model.setIpAddress(entity.getIpAddress());
        model.setHttpMethod(entity.getHttpMethod());
        model.setEndpoint(entity.getEndpoint());
        model.setCorrelationId(entity.getCorrelationId());
        model.setCreatedAt(entity.getCreatedAt());
        return model;
    }

    public static AuditLogResponseDto toResponse(AuditLogModel model) {
        return new AuditLogResponseDto(
                model.getId(), model.getActorId(), model.getAction(), model.getResourceType(),
                model.getResourceId(), toJsonNode(model.getPreviousValue()), toJsonNode(model.getNewValue()),
                model.getResult(), model.getIpAddress(), model.getHttpMethod(), model.getEndpoint(),
                model.getCorrelationId(), model.getCreatedAt());
    }

    private static JsonNode toJsonNode(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(raw);
        } catch (Exception e) {
            // No debería pasar (siempre lo escribimos nosotros mismos vía
            // AuditServiceImpl), pero un JSON corrupto no debe romper el
            // listado: se devuelve tal cual como texto.
            return new TextNode(raw);
        }
    }
}
