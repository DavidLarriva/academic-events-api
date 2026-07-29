package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.controllers;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.dtos.PagedResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.dtos.PaginationDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.dtos.CreateRegistrationDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.dtos.RegistrationFilterDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.dtos.RegistrationResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.dtos.UpdateRegistrationStatusDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.services.RegistrationService;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.annotations.RateLimit;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.enums.RateLimitKeyStrategy;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.enums.RedisKeyPrefix;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services.UserDetailsImpl;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rutas planas (no anidadas bajo /events), igual que
 * /api/registrations/{id}/certificate.pdf en docs/instrucciones.md §13: una
 * inscripción es un recurso propio del participante, no solo un sub-recurso
 * del evento. Lectura (GET) abierta a cualquier rol autenticado, pero con
 * visibilidad scoped por rol dentro del service (a diferencia de
 * events/sessions, acá si importa quién pregunta). Crear queda restringido a
 * PARTICIPANT; el cambio de estado (confirmar/rechazar/cancelar) queda
 * abierto a cualquier rol autenticado porque el service decide, según el
 * status pedido, si quien llama tiene ownership para esa transición
 * específica.
 */
@RestController
@RequestMapping("/registrations")
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @GetMapping
    @RateLimit(prefix = RedisKeyPrefix.RATE_LIMIT_AUTHENTICATED, limit = 120, windowSeconds = 60,
            keyStrategy = RateLimitKeyStrategy.AUTHENTICATED_USER)
    public ResponseEntity<PagedResponseDto<RegistrationResponseDto>> findPage(
            @Valid @ModelAttribute RegistrationFilterDto filters,
            @Valid @ModelAttribute PaginationDto pagination,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(registrationService.findPage(filters, pagination, currentUser));
    }

    @GetMapping("/{id}")
    @RateLimit(prefix = RedisKeyPrefix.RATE_LIMIT_AUTHENTICATED, limit = 120, windowSeconds = 60,
            keyStrategy = RateLimitKeyStrategy.AUTHENTICATED_USER)
    public ResponseEntity<RegistrationResponseDto> findOne(@PathVariable Long id,
                                                             @AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(registrationService.findOne(id, currentUser));
    }

    @PostMapping
    @PreAuthorize("hasRole('PARTICIPANT')")
    @RateLimit(prefix = RedisKeyPrefix.RATE_LIMIT_AUTHENTICATED, limit = 120, windowSeconds = 60,
            keyStrategy = RateLimitKeyStrategy.AUTHENTICATED_USER)
    public ResponseEntity<RegistrationResponseDto> create(@Valid @RequestBody CreateRegistrationDto dto,
                                                            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED).body(registrationService.create(dto, currentUser));
    }

    @PatchMapping("/{id}/status")
    @RateLimit(prefix = RedisKeyPrefix.RATE_LIMIT_AUTHENTICATED, limit = 120, windowSeconds = 60,
            keyStrategy = RateLimitKeyStrategy.AUTHENTICATED_USER)
    public ResponseEntity<RegistrationResponseDto> updateStatus(@PathVariable Long id,
                                                                  @Valid @RequestBody UpdateRegistrationStatusDto dto,
                                                                  @AuthenticationPrincipal UserDetailsImpl currentUser) {
        return ResponseEntity.ok(registrationService.updateStatus(id, dto, currentUser));
    }
}
