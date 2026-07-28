package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.categories.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Reemplazo total (PUT) de los campos mutables. "active" no se maneja acá:
 * se activa/desactiva vía DELETE (eliminación lógica), no por update.
 */
public class UpdateCategoryDto {

    @NotBlank
    @Size(min = 2, max = 80)
    private String name;

    @Size(max = 255)
    private String description;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
