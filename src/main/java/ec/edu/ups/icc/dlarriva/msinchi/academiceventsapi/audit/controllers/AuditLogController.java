package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.audit.controllers;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.audit.dtos.AuditLogFilterDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.audit.dtos.AuditLogResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.audit.services.AuditService;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.dtos.PagedResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.dtos.PaginationDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.annotations.RateLimit;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.enums.RateLimitKeyStrategy;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.enums.RedisKeyPrefix;
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
@RestController
@RequestMapping("/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
public class AuditLogController {

    private final AuditService auditService;

    public AuditLogController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @RateLimit(prefix = RedisKeyPrefix.RATE_LIMIT_AUTHENTICATED, limit = 120, windowSeconds = 60,
            keyStrategy = RateLimitKeyStrategy.AUTHENTICATED_USER)
    public ResponseEntity<PagedResponseDto<AuditLogResponseDto>> findPage(
            @Valid @ModelAttribute AuditLogFilterDto filters,
            @Valid @ModelAttribute PaginationDto pagination) {
        return ResponseEntity.ok(auditService.findPage(filters, pagination));
    }
}
