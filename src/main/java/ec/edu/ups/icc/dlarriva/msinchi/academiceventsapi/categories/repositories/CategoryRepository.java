package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.categories.repositories;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.categories.entities.CategoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<CategoryEntity, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    @Query("""
            SELECT c FROM CategoryEntity c
            WHERE (:name IS NULL OR :name = '' OR LOWER(c.name) LIKE LOWER(CONCAT('%', :name, '%')))
              AND (:active IS NULL OR c.active = :active)
            """)
    Page<CategoryEntity> search(@Param("name") String name, @Param("active") Boolean active, Pageable pageable);
}
