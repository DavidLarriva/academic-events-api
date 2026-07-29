package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.repositories;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.entities.RegistrationEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.enums.RegistrationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface RegistrationRepository extends JpaRepository<RegistrationEntity, Long> {

    /** Usado por events (regla "no eliminar un evento publicado con inscripciones activas", instrucciones.md §9). */
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
}
