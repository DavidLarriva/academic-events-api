package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.controllers;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.dtos.PagedResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.dtos.PaginationDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.dtos.CreateEventDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.dtos.EventFilterDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.dtos.EventResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.dtos.UpdateEventDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.services.EventService;
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
 * Lectura (GET) abierta a cualquier rol autenticado (ADMIN/ORGANIZER/
 * PARTICIPANT, docs/instrucciones.pdf sección 3); mutaciones restringidas a
 * ADMIN/ORGANIZER, con ownership verificado dentro del service (un
 * ORGANIZER solo edita/elimina sus propios eventos, ADMIN accede a todos).
 */
@Tag(name = "Eventos", description = "CRUD de eventos académicos, con ownership por organizador")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
@RestController
@RequestMapping("/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @Operation(summary = "Listar eventos", description = "Paginado, con filtros por categoría, estado, organizador y rango de fechas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de eventos")
    })
    @GetMapping
    @RateLimit(prefix = RedisKeyPrefix.RATE_LIMIT_AUTHENTICATED, limit = 120, windowSeconds = 60,
            keyStrategy = RateLimitKeyStrategy.AUTHENTICATED_USER)
    public ResponseEntity<PagedResponseDto<EventResponseDto>> findPage(
            @Valid @ModelAttribute EventFilterDto filters,
            @Valid @ModelAttribute PaginationDto pagination) {
        return ResponseEntity.ok(eventService.findPage(filters, pagination));
    }

    @Operation(summary = "Obtener un evento por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Evento encontrado"),
            @ApiResponse(responseCode = "404", description = "Evento no encontrado")
    })
    @GetMapping("/{id}")
    @RateLimit(prefix = RedisKeyPrefix.RATE_LIMIT_AUTHENTICATED, limit = 120, windowSeconds = 60,
            keyStrategy = RateLimitKeyStrategy.AUTHENTICATED_USER)
    public ResponseEntity<EventResponseDto> findOne(@PathVariable Long id) {
        return ResponseEntity.ok(eventService.findOne(id));
    }

    @Operation(summary = "Crear evento", description = "El organizador sale siempre del token, nunca del body. Nace en estado DRAFT.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Evento creado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos (fechas, modalidad/ubicación, categoría inactiva)"),
            @ApiResponse(responseCode = "403", description = "Rol insuficiente (ADMIN/ORGANIZER)"),
            @ApiResponse(responseCode = "404", description = "Categoría no encontrada")
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    @RateLimit(prefix = RedisKeyPrefix.RATE_LIMIT_AUTHENTICATED, limit = 120, windowSeconds = 60,
            keyStrategy = RateLimitKeyStrategy.AUTHENTICATED_USER)
    public ResponseEntity<EventResponseDto> create(@Valid @RequestBody CreateEventDto dto,
                                                     @AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventService.create(dto, currentUser));
    }

    @Operation(summary = "Actualizar evento (reemplazo total)",
            description = "Solo el ORGANIZER dueño del evento o ADMIN. La capacidad no puede bajar de lo ya confirmado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Evento actualizado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "403", description = "No es el organizador dueño ni ADMIN"),
            @ApiResponse(responseCode = "404", description = "Evento o categoría no encontrados"),
            @ApiResponse(responseCode = "409", description = "Conflicto de concurrencia (versión desactualizada)")
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    @RateLimit(prefix = RedisKeyPrefix.RATE_LIMIT_AUTHENTICATED, limit = 120, windowSeconds = 60,
            keyStrategy = RateLimitKeyStrategy.AUTHENTICATED_USER)
    public ResponseEntity<EventResponseDto> update(@PathVariable Long id, @Valid @RequestBody UpdateEventDto dto,
                                                     @AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(eventService.update(id, dto, currentUser));
    }

    @Operation(summary = "Eliminar evento (lógico)",
            description = "Solo el ORGANIZER dueño del evento o ADMIN. Rechaza eventos PUBLISHED con inscripciones activas.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Evento eliminado"),
            @ApiResponse(responseCode = "403", description = "No es el organizador dueño ni ADMIN"),
            @ApiResponse(responseCode = "404", description = "Evento no encontrado"),
            @ApiResponse(responseCode = "409", description = "Evento publicado con inscripciones activas")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    @RateLimit(prefix = RedisKeyPrefix.RATE_LIMIT_AUTHENTICATED, limit = 120, windowSeconds = 60,
            keyStrategy = RateLimitKeyStrategy.AUTHENTICATED_USER)
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal UserDetailsImpl currentUser) {
        eventService.delete(id, currentUser);
        return ResponseEntity.noContent().build();
    }
}
