package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.dtos;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services.UserDetailsImpl;
import org.springframework.security.core.GrantedAuthority;

import java.util.Set;
import java.util.stream.Collectors;

public record AuthUserDto(
        Long id,
        String firstName,
        String lastName,
        String email,
        Set<String> roles
) {

    public static AuthUserDto from(UserDetailsImpl principal) {
        Set<String> roles = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        return new AuthUserDto(principal.getId(), principal.getFirstName(), principal.getLastName(),
                principal.getEmail(), roles);
    }
}
