package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.sessions.services;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.dtos.PagedResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.dtos.PaginationDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services.UserDetailsImpl;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.sessions.dtos.CreateSessionDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.sessions.dtos.SessionFilterDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.sessions.dtos.SessionResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.sessions.dtos.UpdateSessionDto;

public interface SessionService {

    PagedResponseDto<SessionResponseDto> findPage(Long eventId, SessionFilterDto filters, PaginationDto pagination);

    SessionResponseDto findOne(Long eventId, Long id);

    SessionResponseDto create(Long eventId, CreateSessionDto dto, UserDetailsImpl currentUser);

    SessionResponseDto update(Long eventId, Long id, UpdateSessionDto dto, UserDetailsImpl currentUser);

    /**
     * Eliminación física: la tabla sessions no tiene columna "deleted"
     * (V1__initial_schema_and_data.sql), a diferencia de events/categories.
     */
    void delete(Long eventId, Long id, UserDetailsImpl currentUser);
}
