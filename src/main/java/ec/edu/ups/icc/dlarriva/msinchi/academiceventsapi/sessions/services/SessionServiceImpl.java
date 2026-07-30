package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.sessions.services;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.dtos.PagedResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.dtos.PaginationDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain.BadRequestException;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain.ConflictException;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain.NotFoundException;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.entities.EventEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.repositories.EventRepository;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services.OwnershipValidator;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services.UserDetailsImpl;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.sessions.dtos.CreateSessionDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.sessions.dtos.SessionFilterDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.sessions.dtos.SessionResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.sessions.dtos.UpdateSessionDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.sessions.entities.SessionEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.sessions.mappers.SessionMapper;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.sessions.repositories.SessionRepository;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Set;

/**
 * Ownership de una sesión = ownership del evento padre (docs/instrucciones.pdf
 * sección 2/sección 3: "ORGANIZER gestiona únicamente sus eventos, sesiones e
 * inscripciones"): no hay un "dueño" propio en la tabla sessions, así que
 * siempre se resuelve vía session.getEvent().getOrganizer().getId() antes de
 * pasarlo a OwnershipValidator (mismo componente reutilizado por events).
 * <p>
 * Reglas de horario acordadas explícitamente con el usuario (no estaban en
 * docs/instrucciones.pdf sección 2, que solo exige el CHECK físico start_at&lt;end_at
 * y el UNIQUE(event_id,title,start_at)):
 * 1) La sesión debe ocurrir dentro del rango [event.startAt, event.endAt].
 * 2) Dos sesiones del mismo evento no pueden solaparse en el tiempo (modelo
 * de una sola sala/track por evento).
 */
@Service
public class SessionServiceImpl implements SessionService {

    private static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of("id", "title", "startAt", "endAt", "createdAt", "updatedAt");
    private static final String DUPLICATE_CODE = "SESSION_DUPLICATE";
    private static final String DUPLICATE_MESSAGE = "Ya existe una sesión con ese título y hora de inicio en este evento";
    private static final String OVERLAP_CODE = "SESSION_OVERLAPS";
    private static final String OVERLAP_MESSAGE = "La sesión se solapa con otra sesión existente en este evento";

    /** Ningún id real es 0: sentinel para "excluir ninguna sesión" al crear. */
    private static final Long NO_SESSION_TO_EXCLUDE = 0L;

    private final SessionRepository sessionRepository;
    private final EventRepository eventRepository;
    private final OwnershipValidator ownershipValidator;
    private final EntityManager entityManager;

    public SessionServiceImpl(SessionRepository sessionRepository, EventRepository eventRepository,
                               OwnershipValidator ownershipValidator, EntityManager entityManager) {
        this.sessionRepository = sessionRepository;
        this.eventRepository = eventRepository;
        this.ownershipValidator = ownershipValidator;
        this.entityManager = entityManager;
    }

    @Override
    public PagedResponseDto<SessionResponseDto> findPage(Long eventId, SessionFilterDto filters,
                                                           PaginationDto pagination) {
        findEventOrThrow(eventId);
        Pageable pageable = pagination.toPageable(ALLOWED_SORT_FIELDS);
        Page<SessionEntity> page = sessionRepository.search(eventId, filters.getTitle(), filters.getStartFrom(),
                filters.getStartTo(), pageable);
        return PagedResponseDto.of(page, entity -> SessionMapper.toResponse(SessionMapper.toModel(entity)));
    }

    @Override
    public SessionResponseDto findOne(Long eventId, Long id) {
        findEventOrThrow(eventId);
        return SessionMapper.toResponse(SessionMapper.toModel(findSessionOrThrow(eventId, id)));
    }

    @Override
    @Transactional
    public SessionResponseDto create(Long eventId, CreateSessionDto dto, UserDetailsImpl currentUser) {
        EventEntity event = findEventOrThrow(eventId);
        ownershipValidator.checkOwnership(event.getOrganizer().getId(), currentUser);

        String title = dto.getTitle().trim();
        validateDateOrder(dto.getStartAt(), dto.getEndAt());
        validateWithinEventRange(event, dto.getStartAt(), dto.getEndAt());
        validateNotDuplicate(eventId, title, dto.getStartAt());
        validateNoOverlap(eventId, NO_SESSION_TO_EXCLUDE, dto.getStartAt(), dto.getEndAt());

        SessionEntity entity = new SessionEntity();
        entity.setEvent(event);
        entity.setTitle(title);
        entity.setDescription(dto.getDescription());
        entity.setStartAt(dto.getStartAt());
        entity.setEndAt(dto.getEndAt());
        entity.setLocation(dto.getLocation());
        entity.setVirtualUrl(dto.getVirtualUrl());

        return SessionMapper.toResponse(SessionMapper.toModel(saveOrThrowConflict(entity)));
    }

    @Override
    @Transactional
    public SessionResponseDto update(Long eventId, Long id, UpdateSessionDto dto, UserDetailsImpl currentUser) {
        SessionEntity entity = findSessionOrThrow(eventId, id);
        ownershipValidator.checkOwnership(entity.getEvent().getOrganizer().getId(), currentUser);

        String title = dto.getTitle().trim();
        validateDateOrder(dto.getStartAt(), dto.getEndAt());
        validateWithinEventRange(entity.getEvent(), dto.getStartAt(), dto.getEndAt());
        validateNotDuplicateForUpdate(eventId, title, dto.getStartAt(), id);
        validateNoOverlap(eventId, id, dto.getStartAt(), dto.getEndAt());

        entity.setTitle(title);
        entity.setDescription(dto.getDescription());
        entity.setStartAt(dto.getStartAt());
        entity.setEndAt(dto.getEndAt());
        entity.setLocation(dto.getLocation());
        entity.setVirtualUrl(dto.getVirtualUrl());

        return SessionMapper.toResponse(SessionMapper.toModel(saveOrThrowConflict(entity)));
    }

    @Override
    @Transactional
    public void delete(Long eventId, Long id, UserDetailsImpl currentUser) {
        SessionEntity entity = findSessionOrThrow(eventId, id);
        ownershipValidator.checkOwnership(entity.getEvent().getOrganizer().getId(), currentUser);
        sessionRepository.delete(entity);
    }

    private EventEntity findEventOrThrow(Long eventId) {
        return eventRepository.findByIdAndDeletedFalse(eventId)
                .orElseThrow(() -> new NotFoundException("EVENT_NOT_FOUND", "Evento no encontrado"));
    }

    private SessionEntity findSessionOrThrow(Long eventId, Long id) {
        return sessionRepository.findByIdAndEvent_Id(id, eventId)
                .orElseThrow(() -> new NotFoundException("SESSION_NOT_FOUND", "Sesión no encontrada"));
    }

    private void validateDateOrder(OffsetDateTime startAt, OffsetDateTime endAt) {
        if (!startAt.isBefore(endAt)) {
            throw new BadRequestException("SESSION_INVALID_DATES", "startAt debe ser anterior a endAt");
        }
    }

    private void validateWithinEventRange(EventEntity event, OffsetDateTime startAt, OffsetDateTime endAt) {
        if (startAt.isBefore(event.getStartAt()) || endAt.isAfter(event.getEndAt())) {
            throw new BadRequestException("SESSION_OUT_OF_EVENT_RANGE",
                    "La sesión debe ocurrir dentro del rango de fechas del evento (" + event.getStartAt() + " - "
                            + event.getEndAt() + ")");
        }
    }

    private void validateNotDuplicate(Long eventId, String title, OffsetDateTime startAt) {
        if (sessionRepository.existsByEvent_IdAndTitleAndStartAt(eventId, title, startAt)) {
            throw new ConflictException(DUPLICATE_CODE, DUPLICATE_MESSAGE);
        }
    }

    private void validateNotDuplicateForUpdate(Long eventId, String title, OffsetDateTime startAt, Long id) {
        if (sessionRepository.existsByEvent_IdAndTitleAndStartAtAndIdNot(eventId, title, startAt, id)) {
            throw new ConflictException(DUPLICATE_CODE, DUPLICATE_MESSAGE);
        }
    }

    private void validateNoOverlap(Long eventId, Long excludeSessionId, OffsetDateTime startAt,
                                    OffsetDateTime endAt) {
        if (sessionRepository.existsOverlapping(eventId, excludeSessionId, startAt, endAt)) {
            throw new ConflictException(OVERLAP_CODE, OVERLAP_MESSAGE);
        }
    }

    /**
     * Igual que Category/EventServiceImpl: la validación previa cubre el
     * caso normal con un 409 legible, pero esto además atrapa la carrera
     * (dos requests concurrentes pasan la validación y ambas insertan) que
     * sí choca contra uq_sessions_event_title_start en la base de datos.
     * created_at/updated_at son insertable=false/updatable=false (los llena
     * el trigger de Postgres), por eso el flush+refresh explícito.
     */
    private SessionEntity saveOrThrowConflict(SessionEntity entity) {
        try {
            SessionEntity saved = sessionRepository.saveAndFlush(entity);
            entityManager.refresh(saved);
            return saved;
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(DUPLICATE_CODE, DUPLICATE_MESSAGE);
        }
    }
}
