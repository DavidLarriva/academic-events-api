package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.audit.controllers;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.audit.dtos.AuditLogFilterDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.audit.dtos.AuditLogResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.audit.services.AuditService;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.dtos.PagedResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.dtos.PaginationDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.annotations.RateLimit;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.config.OpenApiConfig;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.enums.RateLimitKeyStrategy;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.enums.RedisKeyPrefix;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Solo lectura, restringido a ADMIN (docs/instrucciones.md §14/prompt de
 * auditoría). No hay creación manual vía API: las filas las genera
 * AuditServiceImpl desde los propios services de negocio.
 */
@Tag(name = "Auditoría", description = "Listado de solo lectura de audit_logs (solo ADMIN)")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
@RestController
@RequestMapping("/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
public class AuditLogController {

    private final AuditService auditService;

    public AuditLogController(AuditService auditService) {
        this.auditService = auditService;
    }

    @Operation(summary = "Listar registros de auditoría",
            description = "Paginado, con filtros por actor, action, resourceType y rango de fechas (from/to).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de registros de auditoría"),
            @ApiResponse(responseCode = "403", description = "No es ADMIN")
    })
    @GetMapping
    @RateLimit(prefix = RedisKeyPrefix.RATE_LIMIT_AUTHENTICATED, limit = 120, windowSeconds = 60,
            keyStrategy = RateLimitKeyStrategy.AUTHENTICATED_USER)
    public ResponseEntity<PagedResponseDto<AuditLogResponseDto>> findPage(
            @Valid @ModelAttribute AuditLogFilterDto filters,
            @Valid @ModelAttribute PaginationDto pagination) {
        return ResponseEntity.ok(auditService.findPage(filters, pagination));
    }
}
