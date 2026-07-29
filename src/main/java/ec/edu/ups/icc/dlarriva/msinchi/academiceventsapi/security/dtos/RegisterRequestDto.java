package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegisterRequestDto {

    @Schema(description = "Nombres", example = "Ana Lucía")
    @NotBlank
    @Size(max = 80)
    private String firstName;

    @Schema(description = "Apellidos", example = "Torres Paredes")
    @NotBlank
    @Size(max = 80)
    private String lastName;

    @Schema(description = "Correo, único en el sistema", example = "nueva.persona@academic.test")
    @NotBlank
    @Email
    @Size(max = 160)
    private String email;

    @Schema(description = "Mínimo 8 caracteres", example = "Password123*")
    @NotBlank
    @Size(min = 8, max = 72)
    private String password;

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

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
