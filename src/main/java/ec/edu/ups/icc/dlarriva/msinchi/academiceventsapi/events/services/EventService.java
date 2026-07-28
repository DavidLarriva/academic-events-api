package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.services;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.dtos.PagedResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.dtos.PaginationDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.dtos.CreateEventDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.dtos.EventFilterDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.dtos.EventResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.dtos.UpdateEventDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services.UserDetailsImpl;

public interface EventService {

    PagedResponseDto<EventResponseDto> findPage(EventFilterDto filters, PaginationDto pagination);

    EventResponseDto findOne(Long id);

    EventResponseDto create(CreateEventDto dto, UserDetailsImpl currentUser);

    EventResponseDto update(Long id, UpdateEventDto dto, UserDetailsImpl currentUser);

    /**
     * Eliminación lógica (deleted=true). Rechaza con 409 si el evento está
     * PUBLISHED y tiene inscripciones PENDING/CONFIRMED.
     */
    void delete(Long id, UserDetailsImpl currentUser);
}
