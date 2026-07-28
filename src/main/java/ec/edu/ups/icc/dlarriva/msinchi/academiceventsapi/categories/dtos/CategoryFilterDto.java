package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.categories.dtos;

import jakarta.validation.constraints.Size;

/**
 * Filtros de query params para el listado (contexto-materia.md §10.3).
 * name: búsqueda por substring, case-insensitive. active: null = todas,
 * true/false = filtra exacto.
 */
public class CategoryFilterDto {

    @Size(max = 80)
    private String name;

    private Boolean active;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
