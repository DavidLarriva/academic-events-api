package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.repositories;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.entities.EventEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.enums.EventStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface EventRepository extends JpaRepository<EventEntity, Long> {

    Optional<EventEntity> findByIdAndDeletedFalse(Long id);

    /*
     * Cada parámetro aparece UNA sola vez, siempre en un contexto tipado
     * (COALESCE contra la propia columna). El patrón "(:param IS NULL OR
     * campo = :param)" usa cada parámetro DOS veces -> Postgres/JDBC no
     * logra inferir el tipo de la ocurrencia usada solo en "IS NULL"
     * ("could not determine data type of parameter $N"), sobre todo con
     * OffsetDateTime.
     */
    @Query("""
            SELECT e FROM EventEntity e
            WHERE e.deleted = false
              AND e.category.id = COALESCE(:categoryId, e.category.id)
              AND e.status = COALESCE(:status, e.status)
              AND e.organizer.id = COALESCE(:organizerId, e.organizer.id)
              AND e.startAt >= COALESCE(:startFrom, e.startAt)
              AND e.startAt <= COALESCE(:startTo, e.startAt)
            """)
    Page<EventEntity> search(@Param("categoryId") Long categoryId, @Param("status") EventStatus status,
            @Param("organizerId") Long organizerId, @Param("startFrom") OffsetDateTime startFrom,
            @Param("startTo") OffsetDateTime startTo, Pageable pageable);
}
