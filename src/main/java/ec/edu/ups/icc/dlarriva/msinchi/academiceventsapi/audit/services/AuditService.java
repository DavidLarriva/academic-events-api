package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.audit.services;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.audit.dtos.AuditLogFilterDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.audit.dtos.AuditLogResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.audit.enums.AuditResult;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.dtos.PagedResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.dtos.PaginationDto;

import java.util.Map;

/**
 * Invocado explícitamente desde cada service que necesita auditar (AuthServiceImpl,
 * EventServiceImpl, RegistrationServiceImpl), no vía AOP: previous_value/new_value
 * necesitan el diff real de campos, que solo el caller que ya tiene el estado
 * viejo y nuevo en la mano puede construir con precisión (decisión acordada
 * con el usuario). IP/método/endpoint/correlationId se resuelven dentro de
 * la implementación a partir de la request HTTP actual.
 */
public interface AuditService {

    /**
     * actorId puede ser null (ej. LOGIN_FAILED con correo inexistente:
     * V1__initial_schema_and_data.sql modela actor_id nullable justo para
     * este caso). previousValue/newValue: solo los campos que cambiaron,
     * nunca contraseñas, hashes ni tokens.
     */
    void record(Long actorId, String action, String resourceType, Long resourceId,
                Map<String, Object> previousValue, Map<String, Object> newValue, AuditResult result);

    default void recordSuccess(Long actorId, String action, String resourceType, Long resourceId,
                                Map<String, Object> previousValue, Map<String, Object> newValue) {
        record(actorId, action, resourceType, resourceId, previousValue, newValue, AuditResult.SUCCESS);
    }

    default void recordFailure(Long actorId, String action, String resourceType, Long resourceId,
                                Map<String, Object> newValue) {
        record(actorId, action, resourceType, resourceId, null, newValue, AuditResult.FAILED);
    }

    /**
     * Listado de solo lectura para ADMIN, paginado y filtrado.
     */
    PagedResponseDto<AuditLogResponseDto> findPage(AuditLogFilterDto filters, PaginationDto pagination);
}
