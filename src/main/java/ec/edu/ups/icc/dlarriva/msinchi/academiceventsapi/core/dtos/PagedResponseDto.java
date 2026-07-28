package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.dtos;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Envoltorio estándar de respuesta paginada. Cada módulo construye su Page&lt;Entity&gt;
 * con Spring Data (contexto-materia.md §11) y lo adapta aquí, opcionalmente mapeando
 * a un DTO de respuesta.
 */
public record PagedResponseDto<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    public static <T> PagedResponseDto<T> of(Page<T> page) {
        return of(page, Function.identity());
    }

    public static <E, T> PagedResponseDto<T> of(Page<E> page, Function<E, T> mapper) {
        return new PagedResponseDto<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
