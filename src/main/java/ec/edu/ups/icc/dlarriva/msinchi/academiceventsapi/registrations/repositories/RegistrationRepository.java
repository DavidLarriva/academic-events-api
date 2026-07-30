package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.repositories;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.entities.RegistrationEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.enums.RegistrationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RegistrationRepository extends JpaRepository<RegistrationEntity, Long> {

    /** Usado por events (regla "no eliminar un evento publicado con inscripciones activas", instrucciones.pdf sección 9). */
    boolean existsByEvent_IdAndStatusIn(Long eventId, Collection<RegistrationStatus> statuses);

    /**
     * UNIQUE(event_id, participant_id) real de la tabla: como máximo una fila
     * por combinación, sin importar el status. RegistrationServiceImpl la usa
     * para decidir si reabre esa fila (CANCELLED/REJECTED) o si ya hay una
     * inscripción activa (PENDING/CONFIRMED) antes de intentar un insert.
     */
    Optional<RegistrationEntity> findByEvent_IdAndParticipant_Id(Long eventId, Long participantId);

    /*
     * :eventId/:participantId/:organizerId/:status aparecen una sola vez cada
     * uno, siempre en COALESCE contra su propia columna — mismo patrón que
     * events/repositories/EventRepository.java para evitar "could not
     * determine data type of parameter $N". organizerId no es columna directa
     * de registrations: se resuelve vía el evento asociado (r.event.organizer.id).
     */
    @Query("""
            SELECT r FROM RegistrationEntity r
            WHERE r.event.id = COALESCE(:eventId, r.event.id)
              AND r.participant.id = COALESCE(:participantId, r.participant.id)
              AND r.event.organizer.id = COALESCE(:organizerId, r.event.organizer.id)
              AND r.status = COALESCE(:status, r.status)
            """)
    Page<RegistrationEntity> search(@Param("eventId") Long eventId, @Param("participantId") Long participantId,
            @Param("organizerId") Long organizerId, @Param("status") RegistrationStatus status, Pageable pageable);

    /**
     * Para reportes (módulo reports/): sin paginar, JOIN FETCH del
     * participante para no golpear la BD fila por fila al armar el PDF/Excel
     * (nombre/correo de cada inscrito). status/from/to siguen el mismo
     * patrón COALESCE que search(); ReportFilterDto ya trae status=CONFIRMED
     * por defecto, así que acá casi nunca llega null, pero se mantiene el
     * patrón por consistencia y por si alguna vez se llama distinto.
     */
    @Query("""
            SELECT r FROM RegistrationEntity r
            JOIN FETCH r.participant
            WHERE r.event.id = :eventId
              AND r.status = COALESCE(:status, r.status)
              AND r.registeredAt >= COALESCE(:from, r.registeredAt)
              AND r.registeredAt <= COALESCE(:to, r.registeredAt)
            ORDER BY r.registeredAt ASC
            """)
    List<RegistrationEntity> findForReport(@Param("eventId") Long eventId, @Param("status") RegistrationStatus status,
            @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    /**
     * Para el certificado (prompt 18): a diferencia de findForReport, acá sí
     * hace falta más que el id de event/participant (título del evento,
     * modalidad, lugar, nombre/correo del participante) — con
     * spring.jpa.open-in-view=false esos campos no estarían disponibles al
     * armar el PDF sin este JOIN FETCH (la sesión de Hibernate ya se cerró
     * al volver del repository).
     */
    @Query("""
            SELECT r FROM RegistrationEntity r
            JOIN FETCH r.event
            JOIN FETCH r.participant
            WHERE r.id = :id
            """)
    Optional<RegistrationEntity> findByIdWithEventAndParticipant(@Param("id") Long id);
}
