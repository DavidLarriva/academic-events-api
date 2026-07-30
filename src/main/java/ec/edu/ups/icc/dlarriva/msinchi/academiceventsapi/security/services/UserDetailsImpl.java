package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.users.entities.UserEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.users.enums.UserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Adaptador UserEntity -> Spring Security (contexto-materia.md sección 12.5).
 * isEnabled() refleja status ACTIVE/BLOCKED: un usuario BLOCKED no puede
 * autenticarse, pero AuthServiceImpl atrapa cualquier AuthenticationException
 * (incluida DisabledException) y responde el mismo mensaje genérico que unas
 * credenciales inválidas, para no revelar el motivo exacto del rechazo.
 */
public class UserDetailsImpl implements UserDetails {

    private final Long id;
    private final String firstName;
    private final String lastName;
    private final String email;
    private final String passwordHash;
    private final boolean enabled;
    private final Collection<? extends GrantedAuthority> authorities;

    private UserDetailsImpl(Long id, String firstName, String lastName, String email, String passwordHash,
                             boolean enabled, Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
        this.authorities = authorities;
    }

    public static UserDetailsImpl build(UserEntity user) {
        Collection<GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName().name()))
                .collect(Collectors.toList());
        return new UserDetailsImpl(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getStatus() == UserStatus.ACTIVE,
                authorities
        );
    }

    public Long getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public String getEmail() {
        return email;
    }

    /**
     * Acepta el nombre plano del rol (ej. "ADMIN"), igual que hasRole() de
     * Spring Security, sin que quien llama tenga que conocer el prefijo
     * ROLE_ interno de las authorities.
     */
    public boolean hasRole(String roleName) {
        String authority = "ROLE_" + roleName;
        return authorities.stream().anyMatch(a -> a.getAuthority().equals(authority));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
