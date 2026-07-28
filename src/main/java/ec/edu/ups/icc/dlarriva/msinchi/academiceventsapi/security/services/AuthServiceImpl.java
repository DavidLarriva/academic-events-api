package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain.ConflictException;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain.UnauthorizedException;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.dtos.AuthResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.dtos.AuthUserDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.dtos.LoginRequestDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.dtos.RegisterRequestDto;
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

/**
 * Mensajes de autenticación genéricos (docs/instrucciones.md §4): login()
 * atrapa cualquier AuthenticationException (credenciales inválidas, usuario
 * inexistente, cuenta BLOCKED vía UserDetailsImpl.isEnabled()=false) y
 * responde siempre el mismo mensaje/código, para no revelar cuál de esos
 * casos ocurrió. register() sí distingue el correo duplicado con 409
 * (contexto-materia.md §12.10 lo hace explícito), a diferencia de login.
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    public AuthServiceImpl(UserRepository userRepository, RoleRepository roleRepository,
                            PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager,
                            JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
    }

    @Override
    @Transactional
    public AuthResponseDto register(RegisterRequestDto request) {
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

        return buildAuthResponse(UserDetailsImpl.build(user));
    }

    @Override
    public AuthResponseDto login(LoginRequestDto request) {
        String email = normalizeEmail(request.getEmail());
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword()));
        } catch (AuthenticationException e) {
            throw new UnauthorizedException("INVALID_CREDENTIALS", "Correo o contraseña incorrectos");
        }

        UserDetailsImpl principal = (UserDetailsImpl) authentication.getPrincipal();
        return buildAuthResponse(principal);
    }

    private AuthResponseDto buildAuthResponse(UserDetailsImpl principal) {
        String accessToken = jwtUtil.generateAccessToken(principal);
        long expiresIn = jwtUtil.getAccessExpirationMillis() / 1000;
        return new AuthResponseDto(accessToken, "Bearer", expiresIn, AuthUserDto.from(principal));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
