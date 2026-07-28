package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.categories.mappers;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.categories.dtos.CategoryResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.categories.dtos.CreateCategoryDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.categories.entities.CategoryEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.categories.models.CategoryModel;

/**
 * Conversión manual, sin MapStruct (contexto-materia.md §4.4).
 */
public final class CategoryMapper {

    private CategoryMapper() {
    }

    public static CategoryModel toModel(CreateCategoryDto dto) {
        CategoryModel model = new CategoryModel();
        model.setName(dto.getName().trim());
        model.setDescription(dto.getDescription());
        model.setActive(true);
        return model;
    }

    public static CategoryModel toModel(CategoryEntity entity) {
        CategoryModel model = new CategoryModel();
        model.setId(entity.getId());
        model.setName(entity.getName());
        model.setDescription(entity.getDescription());
        model.setActive(entity.isActive());
        model.setCreatedAt(entity.getCreatedAt());
        model.setUpdatedAt(entity.getUpdatedAt());
        return model;
    }

    public static CategoryEntity toEntity(CategoryModel model) {
        CategoryEntity entity = new CategoryEntity();
        entity.setName(model.getName());
        entity.setDescription(model.getDescription());
        entity.setActive(model.isActive());
        return entity;
    }

    public static CategoryResponseDto toResponse(CategoryModel model) {
        return new CategoryResponseDto(model.getId(), model.getName(), model.getDescription(),
                model.isActive(), model.getCreatedAt(), model.getUpdatedAt());
    }
}
