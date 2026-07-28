package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.categories.controllers;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.categories.dtos.CategoryFilterDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.categories.dtos.CategoryResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.categories.dtos.CreateCategoryDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.categories.dtos.UpdateCategoryDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.categories.services.CategoryService;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.dtos.PagedResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.dtos.PaginationDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.annotations.RateLimit;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.enums.RateLimitKeyStrategy;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.enums.RedisKeyPrefix;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CRUD completo restringido a ADMIN (docs/instrucciones.md §3: "ADMIN
 * administra... categorías"), incluida la lectura — si más adelante el
 * módulo de eventos necesita que ORGANIZER/PARTICIPANT lean categorías
 * activas, se agrega ahí un endpoint de lectura propio para ese caso de
 * uso, sin relajar este.
 */
@RestController
@RequestMapping("/categories")
@PreAuthorize("hasRole('ADMIN')")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    @RateLimit(prefix = RedisKeyPrefix.RATE_LIMIT_AUTHENTICATED, limit = 120, windowSeconds = 60,
            keyStrategy = RateLimitKeyStrategy.AUTHENTICATED_USER)
    public ResponseEntity<PagedResponseDto<CategoryResponseDto>> findPage(
            @Valid @ModelAttribute CategoryFilterDto filters,
            @Valid @ModelAttribute PaginationDto pagination) {
        return ResponseEntity.ok(categoryService.findPage(filters, pagination));
    }

    @GetMapping("/{id}")
    @RateLimit(prefix = RedisKeyPrefix.RATE_LIMIT_AUTHENTICATED, limit = 120, windowSeconds = 60,
            keyStrategy = RateLimitKeyStrategy.AUTHENTICATED_USER)
    public ResponseEntity<CategoryResponseDto> findOne(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.findOne(id));
    }

    @PostMapping
    @RateLimit(prefix = RedisKeyPrefix.RATE_LIMIT_AUTHENTICATED, limit = 120, windowSeconds = 60,
            keyStrategy = RateLimitKeyStrategy.AUTHENTICATED_USER)
    public ResponseEntity<CategoryResponseDto> create(@Valid @RequestBody CreateCategoryDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(dto));
    }

    @PutMapping("/{id}")
    @RateLimit(prefix = RedisKeyPrefix.RATE_LIMIT_AUTHENTICATED, limit = 120, windowSeconds = 60,
            keyStrategy = RateLimitKeyStrategy.AUTHENTICATED_USER)
    public ResponseEntity<CategoryResponseDto> update(@PathVariable Long id,
                                                        @Valid @RequestBody UpdateCategoryDto dto) {
        return ResponseEntity.ok(categoryService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @RateLimit(prefix = RedisKeyPrefix.RATE_LIMIT_AUTHENTICATED, limit = 120, windowSeconds = 60,
            keyStrategy = RateLimitKeyStrategy.AUTHENTICATED_USER)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
