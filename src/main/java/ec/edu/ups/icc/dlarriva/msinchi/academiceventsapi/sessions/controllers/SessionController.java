package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.sessions.controllers;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.dtos.PagedResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.dtos.PaginationDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.annotations.RateLimit;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.config.OpenApiConfig;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.enums.RateLimitKeyStrategy;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.enums.RedisKeyPrefix;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services.UserDetailsImpl;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.sessions.dtos.CreateSessionDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.sessions.dtos.SessionFilterDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.sessions.dtos.SessionResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.sessions.dtos.UpdateSessionDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.sessions.services.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anidado bajo el evento (contexto-materia.md sección 10.2: "el recurso principal
 * de la URL da el contexto"), porque una sesión siempre pertenece a un
 * evento (docs/instrucciones.pdf sección 2). Lectura (GET) abierta a cualquier rol
 * autenticado, incluido PARTICIPANT (docs/instrucciones.pdf sección 3: "PARTICIPANT
 * consulta eventos"); mutaciones restringidas a ADMIN/ORGANIZER, con
 * ownership del evento padre verificado dentro del service.
 */
@Tag(name = "Sesiones", description = "CRUD de sesiones/horarios de un evento")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
@RestController
@RequestMapping("/events/{eventId}/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Operation(summary = "Listar sesiones de un evento", description = "Paginado, con filtros por título y rango de fechas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de sesiones"),
            @ApiResponse(responseCode = "404", description = "Evento no encontrado")
    })
    @GetMapping
    @RateLimit(prefix = RedisKeyPrefix.RATE_LIMIT_AUTHENTICATED, limit = 120, windowSeconds = 60,
            keyStrategy = RateLimitKeyStrategy.AUTHENTICATED_USER)
    public ResponseEntity<PagedResponseDto<SessionResponseDto>> findPage(
            @Parameter(description = "Id del evento padre") @PathVariable Long eventId,
            @Valid @ModelAttribute SessionFilterDto filters,
            @Valid @ModelAttribute PaginationDto pagination) {
        return ResponseEntity.ok(sessionService.findPage(eventId, filters, pagination));
    }

    @Operation(summary = "Obtener una sesión por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sesión encontrada"),
            @ApiResponse(responseCode = "404", description = "Evento o sesión no encontrados")
    })
    @GetMapping("/{id}")
    @RateLimit(prefix = RedisKeyPrefix.RATE_LIMIT_AUTHENTICATED, limit = 120, windowSeconds = 60,
            keyStrategy = RateLimitKeyStrategy.AUTHENTICATED_USER)
    public ResponseEntity<SessionResponseDto> findOne(@PathVariable Long eventId, @PathVariable Long id) {
        return ResponseEntity.ok(sessionService.findOne(eventId, id));
    }

    @Operation(summary = "Crear sesión",
            description = "Solo el ORGANIZER dueño del evento o ADMIN. Debe caer dentro del rango de fechas del "
                    + "evento y no solaparse con otra sesión del mismo evento.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Sesión creada"),
            @ApiResponse(responseCode = "400", description = "Fechas inválidas o fuera del rango del evento"),
            @ApiResponse(responseCode = "403", description = "No es el organizador dueño ni ADMIN"),
            @ApiResponse(responseCode = "404", description = "Evento no encontrado"),
            @ApiResponse(responseCode = "409", description = "Duplicado (mismo título+hora) o solapamiento de horario")
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    @RateLimit(prefix = RedisKeyPrefix.RATE_LIMIT_AUTHENTICATED, limit = 120, windowSeconds = 60,
            keyStrategy = RateLimitKeyStrategy.AUTHENTICATED_USER)
    public ResponseEntity<SessionResponseDto> create(@PathVariable Long eventId,
                                                       @Valid @RequestBody CreateSessionDto dto,
                                                       @AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sessionService.create(eventId, dto, currentUser));
    }

    @Operation(summary = "Actualizar sesión (reemplazo total)", description = "Solo el ORGANIZER dueño del evento o ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sesión actualizada"),
            @ApiResponse(responseCode = "400", description = "Fechas inválidas o fuera del rango del evento"),
            @ApiResponse(responseCode = "403", description = "No es el organizador dueño ni ADMIN"),
            @ApiResponse(responseCode = "404", description = "Evento o sesión no encontrados"),
            @ApiResponse(responseCode = "409", description = "Duplicado o solapamiento de horario")
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    @RateLimit(prefix = RedisKeyPrefix.RATE_LIMIT_AUTHENTICATED, limit = 120, windowSeconds = 60,
            keyStrategy = RateLimitKeyStrategy.AUTHENTICATED_USER)
    public ResponseEntity<SessionResponseDto> update(@PathVariable Long eventId, @PathVariable Long id,
                                                       @Valid @RequestBody UpdateSessionDto dto,
                                                       @AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(sessionService.update(eventId, id, dto, currentUser));
    }

    @Operation(summary = "Eliminar sesión (física)", description = "Solo el ORGANIZER dueño del evento o ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Sesión eliminada"),
            @ApiResponse(responseCode = "403", description = "No es el organizador dueño ni ADMIN"),
            @ApiResponse(responseCode = "404", description = "Evento o sesión no encontrados")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    @RateLimit(prefix = RedisKeyPrefix.RATE_LIMIT_AUTHENTICATED, limit = 120, windowSeconds = 60,
            keyStrategy = RateLimitKeyStrategy.AUTHENTICATED_USER)
    public ResponseEntity<Void> delete(@PathVariable Long eventId, @PathVariable Long id,
                                        @AuthenticationPrincipal UserDetailsImpl currentUser) {
        sessionService.delete(eventId, id, currentUser);
        return ResponseEntity.noContent().build();
    }
}
