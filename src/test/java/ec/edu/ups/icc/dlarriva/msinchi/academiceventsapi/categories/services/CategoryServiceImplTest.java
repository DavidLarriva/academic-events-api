package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.categories.services;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.categories.dtos.CategoryResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.categories.dtos.CreateCategoryDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.categories.dtos.UpdateCategoryDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.categories.entities.CategoryEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.categories.repositories.CategoryRepository;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain.ConflictException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private EntityManager entityManager;

    private CategoryServiceImpl categoryService;

    @Test
    void createsCategorySuccessfullyWhenNameIsNotDuplicated() {
        categoryService = new CategoryServiceImpl(categoryRepository, entityManager);

        CreateCategoryDto dto = new CreateCategoryDto();
        dto.setName("Bases de Datos");
        dto.setDescription("Modelado y persistencia");

        when(categoryRepository.existsByNameIgnoreCase("Bases de Datos")).thenReturn(false);
        when(categoryRepository.saveAndFlush(any(CategoryEntity.class))).thenAnswer(invocation -> {
            CategoryEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return entity;
        });

        CategoryResponseDto response = categoryService.create(dto);

        assertEquals(1L, response.id());
        assertEquals("Bases de Datos", response.name());
        assertEquals("Modelado y persistencia", response.description());
        assertTrue(response.active());
        verify(categoryRepository).saveAndFlush(any(CategoryEntity.class));
        verify(entityManager).refresh(any(CategoryEntity.class));
    }

    @Test
    void rejectsDuplicateNameRegardlessOfCase() {
        categoryService = new CategoryServiceImpl(categoryRepository, entityManager);

        CreateCategoryDto dto = new CreateCategoryDto();
        dto.setName("inteligencia artificial");

        when(categoryRepository.existsByNameIgnoreCase("inteligencia artificial")).thenReturn(true);

        ConflictException exception = assertThrows(ConflictException.class, () -> categoryService.create(dto));

        assertEquals("CATEGORY_NAME_DUPLICATE", exception.getCode());
        verify(categoryRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateRejectsDuplicateNameFromAnotherCategoryIgnoringCase() {
        categoryService = new CategoryServiceImpl(categoryRepository, entityManager);

        CategoryEntity existing = new CategoryEntity();
        existing.setId(5L);
        existing.setName("Ciberseguridad");
        existing.setActive(true);

        UpdateCategoryDto updateDto = new UpdateCategoryDto();
        updateDto.setName("CLOUD Y DEVOPS");

        when(categoryRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(categoryRepository.existsByNameIgnoreCaseAndIdNot(eq("CLOUD Y DEVOPS"), eq(5L))).thenReturn(true);

        ConflictException exception = assertThrows(ConflictException.class,
                () -> categoryService.update(5L, updateDto));

        assertEquals("CATEGORY_NAME_DUPLICATE", exception.getCode());
        verify(categoryRepository, never()).saveAndFlush(any());
    }
}
