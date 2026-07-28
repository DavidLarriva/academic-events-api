package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain.ConflictException;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain.UnauthorizedException;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.dtos.AuthResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.dtos.AuthUserDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.dtos.LoginRequestDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.dtos.RefreshTokenRequestDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.dtos.RegisterRequestDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.entities.RefreshTokenEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.entities.RoleEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.enums.RoleName;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.repositories.RoleRepository;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.utils.JwtUtil;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.users.entities.UserEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.users.enums.UserStatus;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.users.repositories.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * Mensajes de autenticación genéricos (docs/instrucciones.md §4): login()
 * atrapa cualquier AuthenticationException (credenciales inválidas, usuario
 * inexistente, cuenta BLOCKED vía UserDetailsImpl.isEnabled()=false) y
 * responde siempre el mismo mensaje/código. register() sí distingue el
 * correo duplicado con 409 (contexto-materia.md §12.10 lo hace explícito).
 * Login/register/refresh ahora también emiten y persisten un refresh token
 * (contexto-materia.md §15).
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttemptService;

    public AuthServiceImpl(UserRepository userRepository, RoleRepository roleRepository,
                            PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager,
                            JwtUtil jwtUtil, RefreshTokenService refreshTokenService,
                            LoginAttemptService loginAttemptService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.refreshTokenService = refreshTokenService;
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    @Transactional
    public AuthResponseDto register(RegisterRequestDto request, String clientIp) {
        String email = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("EMAIL_ALREADY_REGISTERED", "El correo ya está registrado");
        }

        RoleEntity participantRole = roleRepository.findByName(RoleName.PARTICIPANT)
                .orElseThrow(() -> new IllegalStateException(
                        "El rol PARTICIPANT no existe en la base de datos (revisar seed de roles)"));

        UserEntity user = new UserEntity();
        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of(participantRole));

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("EMAIL_ALREADY_REGISTERED", "El correo ya está registrado");
        }

        return buildAuthResponse(user, clientIp);
    }

    @Override
    public AuthResponseDto login(LoginRequestDto request, String clientIp) {
        String email = normalizeEmail(request.getEmail());
        loginAttemptService.checkNotBlocked(clientIp, email);

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword()));
        } catch (AuthenticationException e) {
            loginAttemptService.recordFailure(clientIp, email);
            throw new UnauthorizedException("INVALID_CREDENTIALS", "Correo o contraseña incorrectos");
        }

        UserDetailsImpl principal = (UserDetailsImpl) authentication.getPrincipal();
        UserEntity user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new UnauthorizedException("INVALID_CREDENTIALS", "Correo o contraseña incorrectos"));

        loginAttemptService.recordSuccess(clientIp, email);
        return buildAuthResponse(user, clientIp);
    }

    @Override
    @Transactional
    public AuthResponseDto refresh(RefreshTokenRequestDto request, String clientIp) {
        String rawToken = request.getRefreshToken();
        if (!jwtUtil.validateRefreshToken(rawToken)) {
            throw new UnauthorizedException("INVALID_REFRESH_TOKEN", "Refresh token inválido o expirado");
        }

        UUID tokenId = jwtUtil.getJtiFromToken(rawToken);
        RefreshTokenEntity currentToken = refreshTokenService.validateActive(rawToken, tokenId);

        UserEntity user = userRepository.findById(currentToken.getUser().getId())
                .orElseThrow(() -> new UnauthorizedException("INVALID_REFRESH_TOKEN", "Refresh token inválido o expirado"));

        UUID newTokenId = UUID.randomUUID();
        String newRawRefreshToken = jwtUtil.generateRefreshToken(user.getId(), newTokenId);
        refreshTokenService.issue(user, newTokenId, newRawRefreshToken, clientIp);
        refreshTokenService.rotate(currentToken, newTokenId);

        UserDetailsImpl principal = UserDetailsImpl.build(user);
        String accessToken = jwtUtil.generateAccessToken(principal);
        long expiresIn = jwtUtil.getAccessExpirationMillis() / 1000;
        return new AuthResponseDto(accessToken, "Bearer", expiresIn, newRawRefreshToken, AuthUserDto.from(principal));
    }

    @Override
    @Transactional
    public void logout(RefreshTokenRequestDto request) {
        String rawToken = request.getRefreshToken();
        if (!jwtUtil.validateRefreshToken(rawToken)) {
            return;
        }
        refreshTokenService.revokeIfActive(jwtUtil.getJtiFromToken(rawToken));
    }

    private AuthResponseDto buildAuthResponse(UserEntity user, String clientIp) {
        UserDetailsImpl principal = UserDetailsImpl.build(user);
        String accessToken = jwtUtil.generateAccessToken(principal);
        long expiresIn = jwtUtil.getAccessExpirationMillis() / 1000;

        UUID tokenId = UUID.randomUUID();
        String rawRefreshToken = jwtUtil.generateRefreshToken(user.getId(), tokenId);
        refreshTokenService.issue(user, tokenId, rawRefreshToken, clientIp);

        return new AuthResponseDto(accessToken, "Bearer", expiresIn, rawRefreshToken, AuthUserDto.from(principal));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
