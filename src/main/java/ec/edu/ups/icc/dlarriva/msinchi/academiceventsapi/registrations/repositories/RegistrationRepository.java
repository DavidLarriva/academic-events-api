package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.repositories;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.entities.RegistrationEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.enums.RegistrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

/**
 * Solo lo mínimo que necesita el módulo events (regla "no eliminar un evento
 * publicado con inscripciones activas", instrucciones.md §9). El módulo
 * registrations completo (servicio/controlador) todavía no existe.
 */
public interface RegistrationRepository extends JpaRepository<RegistrationEntity, Long> {

    boolean existsByEvent_IdAndStatusIn(Long eventId, Collection<RegistrationStatus> statuses);
}
