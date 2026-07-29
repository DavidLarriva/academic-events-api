package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.sessions.controllers;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.dtos.PagedResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.dtos.PaginationDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.annotations.RateLimit;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.enums.RateLimitKeyStrategy;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.enums.RedisKeyPrefix;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services.UserDetailsImpl;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.sessions.dtos.CreateSessionDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.sessions.dtos.SessionFilterDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.sessions.dtos.SessionResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.sessions.dtos.UpdateSessionDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.sessions.services.SessionService;
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
 * Anidado bajo el evento (contexto-materia.md §10.2: "el recurso principal
 * de la URL da el contexto"), porque una sesión siempre pertenece a un
 * evento (docs/instrucciones.md §2). Lectura (GET) abierta a cualquier rol
 * autenticado, incluido PARTICIPANT (docs/instrucciones.md §3: "PARTICIPANT
 * consulta eventos"); mutaciones restringidas a ADMIN/ORGANIZER, con
 * ownership del evento padre verificado dentro del service.
 */
@RestController
@RequestMapping("/events/{eventId}/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping
    @RateLimit(prefix = RedisKeyPrefix.RATE_LIMIT_AUTHENTICATED, limit = 120, windowSeconds = 60,
            keyStrategy = RateLimitKeyStrategy.AUTHENTICATED_USER)
    public ResponseEntity<PagedResponseDto<SessionResponseDto>> findPage(
            @PathVariable Long eventId,
            @Valid @ModelAttribute SessionFilterDto filters,
            @Valid @ModelAttribute PaginationDto pagination) {
        return ResponseEntity.ok(sessionService.findPage(eventId, filters, pagination));
    }

    @GetMapping("/{id}")
    @RateLimit(prefix = RedisKeyPrefix.RATE_LIMIT_AUTHENTICATED, limit = 120, windowSeconds = 60,
            keyStrategy = RateLimitKeyStrategy.AUTHENTICATED_USER)
    public ResponseEntity<SessionResponseDto> findOne(@PathVariable Long eventId, @PathVariable Long id) {
        return ResponseEntity.ok(sessionService.findOne(eventId, id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    @RateLimit(prefix = RedisKeyPrefix.RATE_LIMIT_AUTHENTICATED, limit = 120, windowSeconds = 60,
            keyStrategy = RateLimitKeyStrategy.AUTHENTICATED_USER)
    public ResponseEntity<SessionResponseDto> create(@PathVariable Long eventId,
                                                       @Valid @RequestBody CreateSessionDto dto,
                                                       @AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sessionService.create(eventId, dto, currentUser));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    @RateLimit(prefix = RedisKeyPrefix.RATE_LIMIT_AUTHENTICATED, limit = 120, windowSeconds = 60,
            keyStrategy = RateLimitKeyStrategy.AUTHENTICATED_USER)
    public ResponseEntity<SessionResponseDto> update(@PathVariable Long eventId, @PathVariable Long id,
                                                       @Valid @RequestBody UpdateSessionDto dto,
                                                       @AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(sessionService.update(eventId, id, dto, currentUser));
    }

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
