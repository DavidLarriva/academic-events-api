package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.dtos;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain.BadRequestException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

public class PaginationDto {

    @Min(0)
    private int page = 0;

    @Min(1)
    @Max(100)
    private int size = 10;

    private String sortBy = "id";

    private String direction = "asc";

    /**
     * Construye un Pageable validando sortBy contra la lista blanca de campos
     * ordenables del módulo que llama (contexto-materia.md sección 11.5).
     */
    public Pageable toPageable(Set<String> allowedSortFields) {
        if (!allowedSortFields.contains(sortBy)) {
            throw new BadRequestException("INVALID_SORT_FIELD", "Campo no permitido para ordenar: " + sortBy);
        }
        Sort.Direction dir = "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
        return PageRequest.of(page, size, Sort.by(dir, sortBy));
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public String getSortBy() {
        return sortBy;
    }

    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }
}
