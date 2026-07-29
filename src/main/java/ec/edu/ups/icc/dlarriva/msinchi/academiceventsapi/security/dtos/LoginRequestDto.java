package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class LoginRequestDto {

    @Schema(description = "Correo registrado", example = "admin@academic.test")
    @NotBlank
    @Email
    private String email;

    @Schema(description = "Contraseña en texto plano (se compara contra el hash BCrypt)", example = "Password123*")
    @NotBlank
    private String password;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
