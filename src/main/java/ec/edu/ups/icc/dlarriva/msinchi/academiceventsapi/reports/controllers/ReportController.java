package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.reports.controllers;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.reports.dtos.ReportFilterDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.reports.services.ReportService;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.annotations.RateLimit;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.config.OpenApiConfig;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.enums.RateLimitKeyStrategy;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.enums.RedisKeyPrefix;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Archivos generados bajo demanda, en memoria (docs/instrucciones.pdf sección 15:
 * el contenedor no almacena archivos permanentes). Solo ORGANIZER dueño del
 * evento o ADMIN (ownership verificado en ReportServiceImpl).
 */
@Tag(name = "Reportes", description = "Reportes descargables de inscripciones por evento (PDF/Excel)")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
@RestController
@RequestMapping("/reports/events/{eventId}")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
public class ReportController {

    private static final MediaType XLSX_MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @Operation(summary = "Reporte de inscritos en PDF",
            description = "Solo el ORGANIZER dueño del evento o ADMIN. Por defecto incluye solo CONFIRMED; "
                    + "usar status para acotar a otro estado, y from/to para acotar por fecha de inscripción.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Archivo PDF generado"),
            @ApiResponse(responseCode = "403", description = "No es el organizador dueño ni ADMIN"),
            @ApiResponse(responseCode = "404", description = "Evento no encontrado"),
            @ApiResponse(responseCode = "429", description = "Demasiadas solicitudes de reportes")
    })
    @GetMapping(value = "/registrations.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @RateLimit(prefix = RedisKeyPrefix.RATE_LIMIT_REPORTS, limit = 5, windowSeconds = 60,
            keyStrategy = RateLimitKeyStrategy.AUTHENTICATED_USER)
    public ResponseEntity<byte[]> registrationsPdf(@PathVariable Long eventId,
                                                     @Valid @ModelAttribute ReportFilterDto filters,
                                                     @AuthenticationPrincipal UserDetailsImpl currentUser) {
        byte[] pdf = reportService.generateRegistrationsPdf(eventId, filters, currentUser);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"registrations-event-" + eventId + ".pdf\"")
                .body(pdf);
    }

    @Operation(summary = "Reporte de inscritos en Excel", description = "Mismos filtros y reglas de acceso que el PDF.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Archivo Excel generado"),
            @ApiResponse(responseCode = "403", description = "No es el organizador dueño ni ADMIN"),
            @ApiResponse(responseCode = "404", description = "Evento no encontrado"),
            @ApiResponse(responseCode = "429", description = "Demasiadas solicitudes de reportes")
    })
    @GetMapping(value = "/registrations.xlsx",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @RateLimit(prefix = RedisKeyPrefix.RATE_LIMIT_REPORTS, limit = 5, windowSeconds = 60,
            keyStrategy = RateLimitKeyStrategy.AUTHENTICATED_USER)
    public ResponseEntity<byte[]> registrationsExcel(@PathVariable Long eventId,
                                                       @Valid @ModelAttribute ReportFilterDto filters,
                                                       @AuthenticationPrincipal UserDetailsImpl currentUser) {
        byte[] xlsx = reportService.generateRegistrationsExcel(eventId, filters, currentUser);
        return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"registrations-event-" + eventId + ".xlsx\"")
                .body(xlsx);
    }
}
