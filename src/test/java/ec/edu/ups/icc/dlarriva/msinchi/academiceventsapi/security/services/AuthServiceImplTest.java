package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.audit.services.AuditService;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain.ConflictException;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain.UnauthorizedException;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.dtos.AuthResponseDto;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cubre docs/instrucciones.pdf sección 4/sección 5: mensajes de autenticación genéricos
 * (login nunca revela si el correo existe), rotación de refresh token en
 * refresh(), y auditoría de los 5 eventos de AuthServiceImpl.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String CLIENT_IP = "203.0.113.10";

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private LoginAttemptService loginAttemptService;
    @Mock
    private AuditService auditService;
    @Mock
    private Authentication authentication;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(userRepository, roleRepository, passwordEncoder, authenticationManager,
                jwtUtil, refreshTokenService, loginAttemptService, auditService);
    }

    // ---------------------------------------------------------------
    // register
    // ---------------------------------------------------------------

    @Test
    void registerCreatesParticipantWithEncodedPasswordAndAuditsSuccess() {
        RegisterRequestDto dto = registerDto("  Nueva.Persona@Academic.Test  ", "Password123*");
        RoleEntity participantRole = roleOf(RoleName.PARTICIPANT);
        when(userRepository.existsByEmail("nueva.persona@academic.test")).thenReturn(false);
        when(roleRepository.findByName(RoleName.PARTICIPANT)).thenReturn(Optional.of(participantRole));
        when(passwordEncoder.encode("Password123*")).thenReturn("encoded-hash");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity saved = invocation.getArgument(0);
            saved.setId(42L);
            return saved;
        });
        when(jwtUtil.generateAccessToken(any())).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(eq(42L), any(UUID.class))).thenReturn("refresh-token");
        when(jwtUtil.getAccessExpirationMillis()).thenReturn(900_000L);

        AuthResponseDto response = authService.register(dto, CLIENT_IP);

        ArgumentCaptor<UserEntity> savedCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(savedCaptor.capture());
        UserEntity saved = savedCaptor.getValue();
        assertEquals("nueva.persona@academic.test", saved.getEmail());
        assertEquals("encoded-hash", saved.getPasswordHash());
        assertEquals(UserStatus.ACTIVE, saved.getStatus());
        assertEquals(Set.of(participantRole), saved.getRoles());

        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token", response.refreshToken());
        verify(refreshTokenService).issue(eq(saved), any(UUID.class), eq("refresh-token"), eq(CLIENT_IP));
        verify(auditService).recordSuccess(eq(42L), eq("REGISTER_SUCCESS"), eq("USER"), eq(42L), isNull(), any());
    }

    @Test
    void registerRejectsWhenEmailAlreadyExists() {
        RegisterRequestDto dto = registerDto("dup@academic.test", "Password123*");
        when(userRepository.existsByEmail("dup@academic.test")).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class, () -> authService.register(dto, CLIENT_IP));

        assertEquals("EMAIL_ALREADY_REGISTERED", ex.getCode());
        verify(userRepository, never()).save(any());
        verify(auditService, never()).recordSuccess(any(), anyString(), anyString(), any(), any(), any());
    }

    @Test
    void registerTranslatesRaceConditionDuplicateIntoConflict() {
        RegisterRequestDto dto = registerDto("race@academic.test", "Password123*");
        when(userRepository.existsByEmail("race@academic.test")).thenReturn(false);
        when(roleRepository.findByName(RoleName.PARTICIPANT)).thenReturn(Optional.of(roleOf(RoleName.PARTICIPANT)));
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-hash");
        when(userRepository.save(any(UserEntity.class))).thenThrow(new DataIntegrityViolationException("unique_violation"));

        ConflictException ex = assertThrows(ConflictException.class, () -> authService.register(dto, CLIENT_IP));

        assertEquals("EMAIL_ALREADY_REGISTERED", ex.getCode());
    }

    // ---------------------------------------------------------------
    // login: mensajes genéricos (docs/instrucciones.pdf sección 4)
    // ---------------------------------------------------------------

    @Test
    void loginSucceedsAndRecordsSuccessOnBothLoginAttemptServiceAndAudit() {
        LoginRequestDto dto = loginDto("user@academic.test", "Password123*");
        UserEntity user = userEntity(7L, "user@academic.test");
        UserDetailsImpl principal = UserDetailsImpl.build(user);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(jwtUtil.generateAccessToken(any())).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(eq(7L), any(UUID.class))).thenReturn("refresh-token");
        when(jwtUtil.getAccessExpirationMillis()).thenReturn(900_000L);

        AuthResponseDto response = authService.login(dto, CLIENT_IP);

        assertEquals("access-token", response.accessToken());
        verify(loginAttemptService).checkNotBlocked(CLIENT_IP, "user@academic.test");
        verify(loginAttemptService).recordSuccess(CLIENT_IP, "user@academic.test");
        verify(auditService).recordSuccess(eq(7L), eq("LOGIN_SUCCESS"), eq("USER"), eq(7L), isNull(), any());
    }

    @Test
    void loginBlockedByLoginAttemptServiceNeverReachesAuthenticationManager() {
        LoginRequestDto dto = loginDto("blocked@academic.test", "whatever");
        org.mockito.Mockito.doThrow(new UnauthorizedException("INVALID_CREDENTIALS", "Correo o contraseña incorrectos"))
                .when(loginAttemptService).checkNotBlocked(CLIENT_IP, "blocked@academic.test");

        assertThrows(UnauthorizedException.class, () -> authService.login(dto, CLIENT_IP));

        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void loginWithBadCredentialsReturnsGenericMessageAndRecordsFailureWithNullActor() {
        LoginRequestDto dto = loginDto("unknown-or-wrong@academic.test", "wrong-password");
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad creds"));

        UnauthorizedException ex = assertThrows(UnauthorizedException.class, () -> authService.login(dto, CLIENT_IP));

        assertEquals("INVALID_CREDENTIALS", ex.getCode());
        assertEquals("Correo o contraseña incorrectos", ex.getMessage());
        verify(loginAttemptService).recordFailure(CLIENT_IP, "unknown-or-wrong@academic.test");
        verify(auditService).recordFailure(isNull(), eq("LOGIN_FAILED"), eq("USER"), isNull(), any());
    }

    /**
     * docs/instrucciones.pdf sección 4 + seed real (V1__initial_schema_and_data.sql
     * usa hashes con prefijo $2y$, variante OpenBSD de BCrypt). El
     * BCryptPasswordEncoder de Spring Security debe poder verificar ese
     * prefijo igual que el $2a$/$2b$ que genera por defecto, sin necesitar la
     * contraseña real del seed (no está documentada en ningún lado del repo).
     */
    @Test
    void bcryptPasswordEncoderAcceptsSeedStyleDollarTwoYHashes() {
        PasswordEncoder realEncoder = new BCryptPasswordEncoder();
        String rawPassword = "Password123*";
        String encoded = realEncoder.encode(rawPassword);
        assertTrue(encoded.startsWith("$2a$") || encoded.startsWith("$2b$"));

        String seedStyleHash = "$2y$" + encoded.substring(4);

        assertTrue(realEncoder.matches(rawPassword, seedStyleHash));
    }

    // ---------------------------------------------------------------
    // refresh: rotación (contexto-materia.md sección 15)
    // ---------------------------------------------------------------

    @Test
    void refreshRotatesOldTokenAndIssuesNewOne() {
        RefreshTokenRequestDto dto = refreshDto("old-raw-token");
        UUID oldTokenId = UUID.randomUUID();
        UserEntity user = userEntity(9L, "refresh@academic.test");
        RefreshTokenEntity currentToken = new RefreshTokenEntity();
        currentToken.setTokenId(oldTokenId);
        currentToken.setUser(user);

        when(jwtUtil.validateRefreshToken("old-raw-token")).thenReturn(true);
        when(jwtUtil.getJtiFromToken("old-raw-token")).thenReturn(oldTokenId);
        when(refreshTokenService.validateActive("old-raw-token", oldTokenId)).thenReturn(currentToken);
        when(userRepository.findById(9L)).thenReturn(Optional.of(user));
        when(jwtUtil.generateRefreshToken(eq(9L), any(UUID.class))).thenReturn("new-raw-token");
        when(jwtUtil.generateAccessToken(any())).thenReturn("new-access-token");
        when(jwtUtil.getAccessExpirationMillis()).thenReturn(900_000L);

        AuthResponseDto response = authService.refresh(dto, CLIENT_IP);

        assertEquals("new-access-token", response.accessToken());
        assertEquals("new-raw-token", response.refreshToken());
        ArgumentCaptor<UUID> newTokenIdCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(refreshTokenService).issue(eq(user), newTokenIdCaptor.capture(), eq("new-raw-token"), eq(CLIENT_IP));
        verify(refreshTokenService).rotate(eq(currentToken), eq(newTokenIdCaptor.getValue()));
        verify(auditService).recordSuccess(eq(9L), eq("REFRESH_TOKEN_ROTATED"), eq("USER"), eq(9L), any(), any());
    }

    @Test
    void refreshRejectsMalformedOrWrongTypeTokenBeforeTouchingRefreshTokenService() {
        RefreshTokenRequestDto dto = refreshDto("not-a-refresh-token");
        when(jwtUtil.validateRefreshToken("not-a-refresh-token")).thenReturn(false);

        UnauthorizedException ex = assertThrows(UnauthorizedException.class, () -> authService.refresh(dto, CLIENT_IP));

        assertEquals("INVALID_REFRESH_TOKEN", ex.getCode());
        verify(refreshTokenService, never()).validateActive(anyString(), any());
    }

    @Test
    void refreshPropagatesRejectionFromRefreshTokenServiceOnReuseOrExpiry() {
        RefreshTokenRequestDto dto = refreshDto("reused-token");
        UUID tokenId = UUID.randomUUID();
        when(jwtUtil.validateRefreshToken("reused-token")).thenReturn(true);
        when(jwtUtil.getJtiFromToken("reused-token")).thenReturn(tokenId);
        when(refreshTokenService.validateActive("reused-token", tokenId))
                .thenThrow(new UnauthorizedException("INVALID_REFRESH_TOKEN", "Refresh token inválido o expirado"));

        assertThrows(UnauthorizedException.class, () -> authService.refresh(dto, CLIENT_IP));

        verify(userRepository, never()).findById(any());
    }

    // ---------------------------------------------------------------
    // logout: idempotente
    // ---------------------------------------------------------------

    @Test
    void logoutRevokesActiveTokenAndAuditsWithUserIdFromToken() {
        RefreshTokenRequestDto dto = refreshDto("logout-token");
        UUID tokenId = UUID.randomUUID();
        when(jwtUtil.validateRefreshToken("logout-token")).thenReturn(true);
        when(jwtUtil.getJtiFromToken("logout-token")).thenReturn(tokenId);
        when(jwtUtil.getUserIdFromToken("logout-token")).thenReturn(11L);

        authService.logout(dto);

        verify(refreshTokenService).revokeIfActive(tokenId);
        verify(auditService).recordSuccess(eq(11L), eq("LOGOUT"), eq("USER"), eq(11L), isNull(), any());
    }

    @Test
    void logoutWithInvalidTokenIsANoOp() {
        RefreshTokenRequestDto dto = refreshDto("garbage");
        when(jwtUtil.validateRefreshToken("garbage")).thenReturn(false);

        authService.logout(dto);

        verify(refreshTokenService, never()).revokeIfActive(any());
        verify(auditService, never()).recordSuccess(any(), anyString(), anyString(), any(), any(), any());
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private RegisterRequestDto registerDto(String email, String password) {
        RegisterRequestDto dto = new RegisterRequestDto();
        dto.setFirstName("Nueva");
        dto.setLastName("Persona");
        dto.setEmail(email);
        dto.setPassword(password);
        return dto;
    }

    private LoginRequestDto loginDto(String email, String password) {
        LoginRequestDto dto = new LoginRequestDto();
        dto.setEmail(email);
        dto.setPassword(password);
        return dto;
    }

    private RefreshTokenRequestDto refreshDto(String rawToken) {
        RefreshTokenRequestDto dto = new RefreshTokenRequestDto();
        dto.setRefreshToken(rawToken);
        return dto;
    }

    private RoleEntity roleOf(RoleName name) {
        RoleEntity role = new RoleEntity();
        role.setId(1L);
        role.setName(name);
        role.setDescription(name.name());
        return role;
    }

    private UserEntity userEntity(Long id, String email) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of(roleOf(RoleName.PARTICIPANT)));
        return user;
    }
}
