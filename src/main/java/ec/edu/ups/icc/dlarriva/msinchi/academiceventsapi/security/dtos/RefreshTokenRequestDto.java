package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.dtos;

import jakarta.validation.constraints.NotBlank;

/**
 * Usado tanto por POST /auth/refresh como por POST /auth/logout
 * (contexto-materia.md §2.3 / §15.6: mismo shape { "refreshToken": "..." }).
 */
public class RefreshTokenRequestDto {

    @NotBlank
    private String refreshToken;

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
