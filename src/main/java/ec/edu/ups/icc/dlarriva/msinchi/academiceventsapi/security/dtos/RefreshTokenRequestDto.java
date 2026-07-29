package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Usado tanto por POST /auth/refresh como por POST /auth/logout
 * (contexto-materia.md §2.3 / §15.6: mismo shape { "refreshToken": "..." }).
 */
public class RefreshTokenRequestDto {

    @Schema(description = "Refresh token JWT recibido en el login/refresh anterior")
    @NotBlank
    private String refreshToken;

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
