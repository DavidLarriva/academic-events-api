package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.dtos;

public record AuthResponseDto(
        String accessToken,
        String tokenType,
        long expiresIn,
        AuthUserDto user
) {
}
