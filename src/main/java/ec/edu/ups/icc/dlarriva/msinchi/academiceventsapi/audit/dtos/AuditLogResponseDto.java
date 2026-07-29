package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.audit.dtos;

import com.fasterxml.jackson.databind.JsonNode;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.audit.enums.AuditResult;

import java.time.OffsetDateTime;

/**
 * previousValue/newValue como JsonNode (no String): Jackson los serializa
 * como JSON anidado real en la respuesta, no como texto escapado.
 */
public record AuditLogResponseDto(
        Long id,
        Long actorId,
        String action,
        String resourceType,
        Long resourceId,
        JsonNode previousValue,
        JsonNode newValue,
        AuditResult result,
        String ipAddress,
        String httpMethod,
        String endpoint,
        String correlationId,
        OffsetDateTime createdAt
) {
}
