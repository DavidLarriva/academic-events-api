package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.audit.repositories;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.audit.entities.AuditLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {

    /*
     * actor es nullable (LOGIN_FAILED con correo inexistente no tiene actor
     * conocido, ON DELETE SET NULL). El patrón habitual "columna = COALESCE
     * (:param, columna)" (ver EventRepository/CategoryRepository) se rompe
     * ahí: si :actorId es null Y a.actor también es null, NULL = NULL no es
     * verdadero en SQL de tres valores, así que esas filas quedarían
     * excluidas SIEMPRE aunque no se haya pedido filtrar por actor. Se
     * compara contra un sentinel (0, ningún id real lo usa) en ambos lados
     * para que "sin filtro" sea trivialmente cierto sin importar si
     * a.actor es null. action/resourceType/createdAt sí son no-nulos, así
     * que usan el patrón simple de siempre.
     */
    @Query("""
            SELECT a FROM AuditLogEntity a
            WHERE COALESCE(a.actor.id, 0) = COALESCE(:actorId, COALESCE(a.actor.id, 0))
              AND a.action = COALESCE(:action, a.action)
              AND a.resourceType = COALESCE(:resourceType, a.resourceType)
              AND a.createdAt >= COALESCE(:from, a.createdAt)
              AND a.createdAt <= COALESCE(:to, a.createdAt)
            """)
    Page<AuditLogEntity> search(@Param("actorId") Long actorId, @Param("action") String action,
            @Param("resourceType") String resourceType, @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to, Pageable pageable);
}
