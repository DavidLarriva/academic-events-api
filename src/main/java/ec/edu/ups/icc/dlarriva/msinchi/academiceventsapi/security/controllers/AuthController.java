package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.controllers;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.annotations.RateLimit;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.config.OpenApiConfig;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Autenticación", description = "Registro, login, refresh/rotación de tokens, logout y usuario autenticado")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final ClientIpResolver clientIpResolver;

    public AuthController(AuthService authService, ClientIpResolver clientIpResolver) {
        this.authService = authService;
        this.clientIpResolver = clientIpResolver;
    }

    @Operation(summary = "Registrar un nuevo usuario",
            description = "Crea el usuario con rol PARTICIPANT y devuelve login automático (access + refresh token).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuario creado y autenticado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "409", description = "El correo ya está registrado"),
            @ApiResponse(responseCode = "429", description = "Demasiadas solicitudes de registro desde esta IP")
    })
    @PostMapping("/register")
    @RateLimit(prefix = RedisKeyPrefix.RATE_LIMIT_REGISTER, limit = 3, windowSeconds = 3600,
            keyStrategy = RateLimitKeyStrategy.IP)
    public ResponseEntity<AuthResponseDto> register(@Valid @RequestBody RegisterRequestDto request,
                                                      HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.register(request, clientIpResolver.resolve(httpRequest)));
    }

    @Operation(summary = "Iniciar sesión",
            description = "Mensaje genérico si el correo no existe o la contraseña es incorrecta (no distingue el motivo).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Autenticado"),
            @ApiResponse(responseCode = "401", description = "Correo o contraseña incorrectos"),
            @ApiResponse(responseCode = "429", description = "Demasiados intentos de login (IP + correo)")
    })
    @PostMapping("/login")
    @RateLimit(prefix = RedisKeyPrefix.RATE_LIMIT_LOGIN, limit = 5, windowSeconds = 60,
            keyStrategy = RateLimitKeyStrategy.IP_AND_LOGIN_EMAIL)
    public ResponseEntity<AuthResponseDto> login(@Valid @RequestBody LoginRequestDto request,
                                                  HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.login(request, clientIpResolver.resolve(httpRequest)));
    }

    @Operation(summary = "Renovar sesión (rotación de refresh token)",
            description = "Invalida el refresh token recibido y devuelve un access token y un refresh token nuevos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tokens renovados"),
            @ApiResponse(responseCode = "401", description = "Refresh token inválido, expirado o ya revocado")
    })
    @PostMapping("/refresh")
    @RateLimit(prefix = RedisKeyPrefix.RATE_LIMIT_PUBLIC, limit = 60, windowSeconds = 60,
            keyStrategy = RateLimitKeyStrategy.IP)
    public ResponseEntity<AuthResponseDto> refresh(@Valid @RequestBody RefreshTokenRequestDto request,
                                                     HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.refresh(request, clientIpResolver.resolve(httpRequest)));
    }

    @Operation(summary = "Cerrar sesión", description = "Revoca el refresh token recibido. Idempotente.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Sesión cerrada (o ya lo estaba)")
    })
    @PostMapping("/logout")
    @RateLimit(prefix = RedisKeyPrefix.RATE_LIMIT_PUBLIC, limit = 60, windowSeconds = 60,
            keyStrategy = RateLimitKeyStrategy.IP)
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequestDto request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Usuario autenticado actual")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Datos del usuario autenticado"),
            @ApiResponse(responseCode = "401", description = "Sin token o token inválido")
    })
    @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
    @GetMapping("/me")
    @RateLimit(prefix = RedisKeyPrefix.RATE_LIMIT_AUTHENTICATED, limit = 120, windowSeconds = 60,
            keyStrategy = RateLimitKeyStrategy.AUTHENTICATED_USER)
    public ResponseEntity<AuthUserDto> me(@AuthenticationPrincipal UserDetailsImpl principal) {
        return ResponseEntity.ok(AuthUserDto.from(principal));
    }
}
