package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.services;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.dtos.PagedResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.dtos.PaginationDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.dtos.CreateRegistrationDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.dtos.RegistrationFilterDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.dtos.RegistrationResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.dtos.UpdateRegistrationStatusDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services.UserDetailsImpl;

public interface RegistrationService {

    /**
     * Listado scoped por rol: PARTICIPANT ve solo las suyas, ORGANIZER ve
     * las de los eventos que organiza, ADMIN ve todas (con filtros libres).
     */
    PagedResponseDto<RegistrationResponseDto> findPage(RegistrationFilterDto filters, PaginationDto pagination,
                                                         UserDetailsImpl currentUser);

    RegistrationResponseDto findOne(Long id, UserDetailsImpl currentUser);

    /**
     * PENDING nueva, o reabre (vuelve a PENDING) una fila CANCELLED/REJECTED
     * existente para el mismo evento+participante (UNIQUE de la tabla).
     */
    RegistrationResponseDto create(CreateRegistrationDto dto, UserDetailsImpl currentUser);

    /**
     * Único punto de transición de estado: CONFIRMED/REJECTED (ORGANIZER
     * dueño del evento o ADMIN) y CANCELLED (PARTICIPANT dueño o ADMIN).
     */
    RegistrationResponseDto updateStatus(Long id, UpdateRegistrationStatusDto dto, UserDetailsImpl currentUser);
}
