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

    /*
     * :name/:active aparecen una sola vez cada uno, siempre en contexto
     * tipado. El patrón "(:param IS NULL OR ...)" usa cada parámetro dos
     * veces y puede hacer que Postgres/JDBC falle con "could not determine
     * data type of parameter $N" una vez que Hibernate empieza a preparar
     * la sentencia del lado del servidor (ver events/repositories/
     * EventRepository.java, donde sí llegó a fallar en vivo).
     */
    @Query("""
            SELECT c FROM CategoryEntity c
            WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', COALESCE(:name, ''), '%'))
              AND c.active = COALESCE(:active, c.active)
            """)
    Page<CategoryEntity> search(@Param("name") String name, @Param("active") Boolean active, Pageable pageable);
}
