package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.controllers;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.dtos.AuthResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.dtos.AuthUserDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.dtos.LoginRequestDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.dtos.RefreshTokenRequestDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.dtos.RegisterRequestDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services.AuthService;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services.UserDetailsImpl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rutas relativas: el context-path /api las convierte en /api/auth/**.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDto> register(@Valid @RequestBody RegisterRequestDto request,
                                                      HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.register(request, clientIp(httpRequest)));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(@Valid @RequestBody LoginRequestDto request,
                                                  HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.login(request, clientIp(httpRequest)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponseDto> refresh(@Valid @RequestBody RefreshTokenRequestDto request,
                                                     HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.refresh(request, clientIp(httpRequest)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequestDto request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<AuthUserDto> me(@AuthenticationPrincipal UserDetailsImpl principal) {
        return ResponseEntity.ok(AuthUserDto.from(principal));
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
