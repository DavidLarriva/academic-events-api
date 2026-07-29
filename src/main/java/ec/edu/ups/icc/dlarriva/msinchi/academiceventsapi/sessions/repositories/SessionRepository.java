package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.sessions.repositories;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.sessions.entities.SessionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<SessionEntity, Long> {

    /**
     * Asegura que el id pedido por la URL realmente pertenece al evento
     * padre de la URL (/events/{eventId}/sessions/{id}); evita que alguien
     * lea/edite una sesión de otro evento adivinando el id.
     */
    Optional<SessionEntity> findByIdAndEvent_Id(Long id, Long eventId);

    boolean existsByEvent_IdAndTitleAndStartAt(Long eventId, String title, OffsetDateTime startAt);

    boolean existsByEvent_IdAndTitleAndStartAtAndIdNot(Long eventId, String title, OffsetDateTime startAt, Long id);

    /*
     * :eventId/:excludeSessionId/:startAt/:endAt aparecen una sola vez cada
     * uno, siempre en un contexto tipado (comparación directa contra una
     * columna), nunca en un "(:param IS NULL OR ...)" — ver el comentario en
     * events/repositories/EventRepository.java sobre por qué ese patrón
     * puede romper con "could not determine data type of parameter $N". El
     * caller pasa 0L como excludeSessionId al crear (ningún id real es 0),
     * en vez de null, para no tener que resolver ese caso acá.
     */
    @Query("""
            SELECT COUNT(s) > 0 FROM SessionEntity s
            WHERE s.event.id = :eventId
              AND s.id <> :excludeSessionId
              AND s.startAt < :endAt
              AND s.endAt > :startAt
            """)
    boolean existsOverlapping(@Param("eventId") Long eventId, @Param("excludeSessionId") Long excludeSessionId,
            @Param("startAt") OffsetDateTime startAt, @Param("endAt") OffsetDateTime endAt);

    @Query("""
            SELECT s FROM SessionEntity s
            WHERE s.event.id = :eventId
              AND LOWER(s.title) LIKE LOWER(CONCAT('%', COALESCE(:title, ''), '%'))
              AND s.startAt >= COALESCE(:startFrom, s.startAt)
              AND s.startAt <= COALESCE(:startTo, s.startAt)
            """)
    Page<SessionEntity> search(@Param("eventId") Long eventId, @Param("title") String title,
            @Param("startFrom") OffsetDateTime startFrom, @Param("startTo") OffsetDateTime startTo,
            Pageable pageable);
}
