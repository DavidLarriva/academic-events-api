package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.categories.services;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.categories.dtos.CategoryFilterDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.categories.dtos.CategoryResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.categories.dtos.CreateCategoryDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.categories.dtos.UpdateCategoryDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.dtos.PagedResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.dtos.PaginationDto;

public interface CategoryService {

    PagedResponseDto<CategoryResponseDto> findPage(CategoryFilterDto filters, PaginationDto pagination);

    CategoryResponseDto findOne(Long id);

    CategoryResponseDto create(CreateCategoryDto dto);

    CategoryResponseDto update(Long id, UpdateCategoryDto dto);

    /**
     * Eliminación lógica: pone active=false. No borra la fila.
     */
    void delete(Long id);
}
