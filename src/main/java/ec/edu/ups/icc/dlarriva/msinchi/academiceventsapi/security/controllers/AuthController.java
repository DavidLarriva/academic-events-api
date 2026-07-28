package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.controllers;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.annotations.RateLimit;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.dtos.AuthResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.dtos.AuthUserDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.dtos.LoginRequestDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.dtos.RefreshTokenRequestDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.dtos.RegisterRequestDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.enums.RateLimitKeyStrategy;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.enums.RedisKeyPrefix;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services.AuthService;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services.UserDetailsImpl;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.utils.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rutas relativas: el context-path /api las convierte en /api/auth/**.
 * Límites de docs/instrucciones.md §7: login y registro tienen su propia
 * categoría; refresh/logout son públicos (no autenticados) así que caen en
 * la categoría genérica de endpoints públicos; /me sí requiere token, así
 * que usa la categoría de endpoints autenticados.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final ClientIpResolver clientIpResolver;

    public AuthController(AuthService authService, ClientIpResolver clientIpResolver) {
        this.authService = authService;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/register")
    @RateLimit(prefix = RedisKeyPrefix.RATE_LIMIT_REGISTER, limit = 3, windowSeconds = 3600,
            keyStrategy = RateLimitKeyStrategy.IP)
    public ResponseEntity<AuthResponseDto> register(@Valid @RequestBody RegisterRequestDto request,
                                                      HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.register(request, clientIpResolver.resolve(httpRequest)));
    }

    @PostMapping("/login")
    @RateLimit(prefix = RedisKeyPrefix.RATE_LIMIT_LOGIN, limit = 5, windowSeconds = 60,
            keyStrategy = RateLimitKeyStrategy.IP_AND_LOGIN_EMAIL)
    public ResponseEntity<AuthResponseDto> login(@Valid @RequestBody LoginRequestDto request,
                                                  HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.login(request, clientIpResolver.resolve(httpRequest)));
    }

    @PostMapping("/refresh")
    @RateLimit(prefix = RedisKeyPrefix.RATE_LIMIT_PUBLIC, limit = 60, windowSeconds = 60,
            keyStrategy = RateLimitKeyStrategy.IP)
    public ResponseEntity<AuthResponseDto> refresh(@Valid @RequestBody RefreshTokenRequestDto request,
                                                     HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.refresh(request, clientIpResolver.resolve(httpRequest)));
    }

    @PostMapping("/logout")
    @RateLimit(prefix = RedisKeyPrefix.RATE_LIMIT_PUBLIC, limit = 60, windowSeconds = 60,
            keyStrategy = RateLimitKeyStrategy.IP)
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequestDto request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @RateLimit(prefix = RedisKeyPrefix.RATE_LIMIT_AUTHENTICATED, limit = 120, windowSeconds = 60,
            keyStrategy = RateLimitKeyStrategy.AUTHENTICATED_USER)
    public ResponseEntity<AuthUserDto> me(@AuthenticationPrincipal UserDetailsImpl principal) {
        return ResponseEntity.ok(AuthUserDto.from(principal));
    }
}
