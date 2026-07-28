package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.categories.services;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.categories.dtos.CategoryFilterDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.categories.dtos.CategoryResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.categories.dtos.CreateCategoryDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.categories.dtos.UpdateCategoryDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.categories.entities.CategoryEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.categories.mappers.CategoryMapper;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.categories.models.CategoryModel;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.categories.repositories.CategoryRepository;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.dtos.PagedResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.dtos.PaginationDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain.ConflictException;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain.NotFoundException;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * "active" (no "deleted") es la eliminación lógica de categories
 * (categories/entities/CategoryEntity.java, prompt 3). El nombre único
 * case-insensitive se valida acá (no solo se confía en el índice de BD,
 * uq_categories_name_lower) para devolver un 409 prolijo vía
 * GlobalExceptionHandler en vez de un error crudo de constraint violation.
 */
@Service
public class CategoryServiceImpl implements CategoryService {

    private static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of("id", "name", "active", "createdAt", "updatedAt");
    private static final String DUPLICATE_CODE = "CATEGORY_NAME_DUPLICATE";
    private static final String DUPLICATE_MESSAGE = "Ya existe una categoría con ese nombre";
    private static final String NOT_FOUND_CODE = "CATEGORY_NOT_FOUND";
    private static final String NOT_FOUND_MESSAGE = "Categoría no encontrada";

    private final CategoryRepository categoryRepository;
    private final EntityManager entityManager;

    public CategoryServiceImpl(CategoryRepository categoryRepository, EntityManager entityManager) {
        this.categoryRepository = categoryRepository;
        this.entityManager = entityManager;
    }

    @Override
    public PagedResponseDto<CategoryResponseDto> findPage(CategoryFilterDto filters, PaginationDto pagination) {
        Pageable pageable = pagination.toPageable(ALLOWED_SORT_FIELDS);
        Page<CategoryEntity> page = categoryRepository.search(filters.getName(), filters.getActive(), pageable);
        return PagedResponseDto.of(page, entity -> CategoryMapper.toResponse(CategoryMapper.toModel(entity)));
    }

    @Override
    public CategoryResponseDto findOne(Long id) {
        return CategoryMapper.toResponse(CategoryMapper.toModel(findEntityOrThrow(id)));
    }

    @Override
    @Transactional
    public CategoryResponseDto create(CreateCategoryDto dto) {
        String name = dto.getName().trim();
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException(DUPLICATE_CODE, DUPLICATE_MESSAGE);
        }

        CategoryModel model = CategoryMapper.toModel(dto);
        CategoryEntity entity = CategoryMapper.toEntity(model);

        entity = saveOrThrowConflict(entity);
        return CategoryMapper.toResponse(CategoryMapper.toModel(entity));
    }

    @Override
    @Transactional
    public CategoryResponseDto update(Long id, UpdateCategoryDto dto) {
        CategoryEntity entity = findEntityOrThrow(id);

        String name = dto.getName().trim();
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException(DUPLICATE_CODE, DUPLICATE_MESSAGE);
        }

        entity.setName(name);
        entity.setDescription(dto.getDescription());

        entity = saveOrThrowConflict(entity);
        return CategoryMapper.toResponse(CategoryMapper.toModel(entity));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        CategoryEntity entity = findEntityOrThrow(id);
        entity.setActive(false);
        categoryRepository.save(entity);
    }

    private CategoryEntity findEntityOrThrow(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(NOT_FOUND_CODE, NOT_FOUND_MESSAGE));
    }

    /**
     * created_at/updated_at son insertable=false/updatable=false (los llena
     * Postgres vía DEFAULT/trigger, contexto-materia.md §6.3 no aplica tal
     * cual acá porque el schema real es distinto — ver CategoryEntity). Sin
     * el flush+refresh explícitos, el objeto en memoria se queda con esos
     * campos en null tras el insert/update y la respuesta los expone así.
     */
    private CategoryEntity saveOrThrowConflict(CategoryEntity entity) {
        try {
            CategoryEntity saved = categoryRepository.saveAndFlush(entity);
            entityManager.refresh(saved);
            return saved;
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(DUPLICATE_CODE, DUPLICATE_MESSAGE);
        }
    }
}
