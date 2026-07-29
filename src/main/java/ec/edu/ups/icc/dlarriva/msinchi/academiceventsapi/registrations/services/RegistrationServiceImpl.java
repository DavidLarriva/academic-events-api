package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.services;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.dtos.PagedResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.dtos.PaginationDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain.BadRequestException;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain.ConflictException;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain.ForbiddenException;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain.NotFoundException;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.entities.EventEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.enums.EventStatus;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.repositories.EventRepository;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.dtos.CreateRegistrationDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.dtos.RegistrationFilterDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.dtos.RegistrationResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.dtos.UpdateRegistrationStatusDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.entities.RegistrationEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.enums.RegistrationStatus;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.mappers.RegistrationMapper;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.repositories.RegistrationRepository;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services.OwnershipValidator;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services.UserDetailsImpl;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.users.entities.UserEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.users.repositories.UserRepository;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Flujo de 4 estados definido por el schema real (V1__initial_schema_and_data.sql,
 * comentario "Solo las inscripciones CONFIRMED consumen cupo"): el cupo de
 * events.available_capacity NO se descuenta al crear (PENDING), solo al
 * confirmar; y se devuelve si una CONFIRMED se cancela. Ambos ajustes viven
 * en la MISMA transacción que el cambio de estado.
 * <p>
 * Decisiones acordadas explícitamente con el usuario (no estaban resueltas
 * en docs/instrucciones.md):
 * 1) CONFIRMED/REJECTED: solo el ORGANIZER dueño del evento o ADMIN.
 * 2) Cancelar (CANCELLED): sin plazo límite, salvo que el evento ya esté
 * FINISHED (ahí ya no tiene sentido cancelar).
 * 3) Al reintentar inscribirse, se reabre (vuelve a PENDING) la fila
 * existente tanto si estaba CANCELLED como si estaba REJECTED — el
 * UNIQUE(event_id, participant_id) de la tabla no distingue por status,
 * así que sin esto un REJECTED quedaría bloqueado para siempre.
 * <p>
 * Además, la ventana [registrationStartAt, registrationEndAt] del evento
 * (ya modelada en EventEntity con ese propósito explícito) se valida al
 * crear: no es una regla inventada, es la razón de ser de esas dos columnas.
 */
@Service
public class RegistrationServiceImpl implements RegistrationService {

    private static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of("id", "status", "registeredAt", "statusUpdatedAt", "confirmedAt", "cancelledAt");
    private static final Set<RegistrationStatus> ACTIVE_STATUSES =
            Set.of(RegistrationStatus.PENDING, RegistrationStatus.CONFIRMED);

    private final RegistrationRepository registrationRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final OwnershipValidator ownershipValidator;
    private final EntityManager entityManager;

    public RegistrationServiceImpl(RegistrationRepository registrationRepository, EventRepository eventRepository,
                                    UserRepository userRepository, OwnershipValidator ownershipValidator,
                                    EntityManager entityManager) {
        this.registrationRepository = registrationRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.ownershipValidator = ownershipValidator;
        this.entityManager = entityManager;
    }

    @Override
    public PagedResponseDto<RegistrationResponseDto> findPage(RegistrationFilterDto filters, PaginationDto pagination,
                                                                UserDetailsImpl currentUser) {
        Pageable pageable = pagination.toPageable(ALLOWED_SORT_FIELDS);
        Page<RegistrationEntity> page = searchScopedByRole(filters, currentUser, pageable);
        return PagedResponseDto.of(page, entity -> RegistrationMapper.toResponse(RegistrationMapper.toModel(entity)));
    }

    /**
     * ADMIN > ORGANIZER > PARTICIPANT: un usuario con varios roles (ej. los
     * seed 2/3/4, ORGANIZER+PARTICIPANT) ve el listado con el alcance del rol
     * más amplio que tenga, en vez de mezclar ambos alcances en una sola
     * consulta.
     */
    private Page<RegistrationEntity> searchScopedByRole(RegistrationFilterDto filters, UserDetailsImpl currentUser,
                                                          Pageable pageable) {
        if (currentUser.hasRole("ADMIN")) {
            return registrationRepository.search(filters.getEventId(), filters.getParticipantId(), null,
                    filters.getStatus(), pageable);
        }
        if (currentUser.hasRole("ORGANIZER")) {
            return registrationRepository.search(filters.getEventId(), null, currentUser.getId(),
                    filters.getStatus(), pageable);
        }
        return registrationRepository.search(filters.getEventId(), currentUser.getId(), null, filters.getStatus(),
                pageable);
    }

    @Override
    public RegistrationResponseDto findOne(Long id, UserDetailsImpl currentUser) {
        RegistrationEntity entity = findEntityOrThrow(id);
        boolean canView = ownershipValidator.isOwner(entity.getParticipant().getId(), currentUser)
                || ownershipValidator.isOwner(entity.getEvent().getOrganizer().getId(), currentUser);
        if (!canView) {
            throw new ForbiddenException("NOT_RESOURCE_OWNER", "No tiene permisos sobre esta inscripción");
        }
        return RegistrationMapper.toResponse(RegistrationMapper.toModel(entity));
    }

    @Override
    @Transactional
    public RegistrationResponseDto create(CreateRegistrationDto dto, UserDetailsImpl currentUser) {
        EventEntity event = eventRepository.findByIdAndDeletedFalse(dto.getEventId())
                .orElseThrow(() -> new NotFoundException("EVENT_NOT_FOUND", "Evento no encontrado"));
        validateEventOpenForRegistration(event);

        UserEntity participant = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "Usuario no encontrado"));

        RegistrationEntity entity = registrationRepository
                .findByEvent_IdAndParticipant_Id(event.getId(), participant.getId())
                .map(this::reopenOrRejectDuplicate)
                .orElseGet(() -> newRegistration(event, participant));

        return RegistrationMapper.toResponse(RegistrationMapper.toModel(saveAndRefresh(entity)));
    }

    private void validateEventOpenForRegistration(EventEntity event) {
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new BadRequestException("EVENT_NOT_OPEN_FOR_REGISTRATION",
                    "El evento no está publicado (estado actual: " + event.getStatus() + ")");
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (now.isBefore(event.getRegistrationStartAt())) {
            throw new BadRequestException("EVENT_REGISTRATION_NOT_STARTED",
                    "El periodo de inscripciones de este evento todavía no comienza");
        }
        if (now.isAfter(event.getRegistrationEndAt())) {
            throw new BadRequestException("EVENT_REGISTRATION_CLOSED",
                    "El periodo de inscripciones de este evento ya finalizó");
        }
        if (event.getAvailableCapacity() <= 0) {
            throw new ConflictException("EVENT_NO_CAPACITY", "No hay cupos disponibles para este evento");
        }
    }

    /**
     * UNIQUE(event_id, participant_id): como máximo una fila por combinación,
     * sin importar el status. Si ya hay una activa, es un intento de doble
     * inscripción; si estaba CANCELLED/REJECTED, se reabre esa misma fila en
     * vez de intentar un insert que la constraint rechazaría igual.
     */
    private RegistrationEntity reopenOrRejectDuplicate(RegistrationEntity existing) {
        if (ACTIVE_STATUSES.contains(existing.getStatus())) {
            throw new ConflictException("REGISTRATION_ALREADY_ACTIVE",
                    "Ya tiene una inscripción activa para este evento");
        }
        existing.setStatus(RegistrationStatus.PENDING);
        existing.setStatusUpdatedAt(OffsetDateTime.now());
        existing.setConfirmedAt(null);
        existing.setCancelledAt(null);
        return existing;
    }

    private RegistrationEntity newRegistration(EventEntity event, UserEntity participant) {
        RegistrationEntity entity = new RegistrationEntity();
        entity.setRegistrationCode(UUID.randomUUID());
        entity.setEvent(event);
        entity.setParticipant(participant);
        entity.setStatus(RegistrationStatus.PENDING);
        return entity;
    }

    @Override
    @Transactional
    public RegistrationResponseDto updateStatus(Long id, UpdateRegistrationStatusDto dto, UserDetailsImpl currentUser) {
        RegistrationEntity entity = findEntityOrThrow(id);

        switch (dto.getStatus()) {
            case CONFIRMED -> confirm(entity, currentUser);
            case REJECTED -> reject(entity, currentUser);
            case CANCELLED -> cancel(entity, currentUser);
            case PENDING -> throw new BadRequestException("INVALID_STATUS_TRANSITION",
                    "No se puede volver una inscripción a PENDING mediante este endpoint");
        }

        return RegistrationMapper.toResponse(RegistrationMapper.toModel(saveAndRefresh(entity)));
    }

    private void confirm(RegistrationEntity entity, UserDetailsImpl currentUser) {
        ownershipValidator.checkOwnership(entity.getEvent().getOrganizer().getId(), currentUser);
        requireTransitionFrom(entity, RegistrationStatus.PENDING, RegistrationStatus.CONFIRMED);

        // Se revalida el cupo acá, no solo al crear la PENDING: entre la
        // creación y la confirmación puede haberse agotado por otras
        // confirmaciones (condición de carrera de cupo agotado).
        EventEntity event = entity.getEvent();
        if (event.getAvailableCapacity() <= 0) {
            throw new ConflictException("EVENT_NO_CAPACITY", "No hay cupos disponibles para confirmar esta inscripción");
        }
        event.setAvailableCapacity(event.getAvailableCapacity() - 1);
        eventRepository.save(event);

        entity.setStatus(RegistrationStatus.CONFIRMED);
        entity.setConfirmedAt(OffsetDateTime.now());
        entity.setStatusUpdatedAt(OffsetDateTime.now());
    }

    private void reject(RegistrationEntity entity, UserDetailsImpl currentUser) {
        ownershipValidator.checkOwnership(entity.getEvent().getOrganizer().getId(), currentUser);
        requireTransitionFrom(entity, RegistrationStatus.PENDING, RegistrationStatus.REJECTED);

        entity.setStatus(RegistrationStatus.REJECTED);
        entity.setStatusUpdatedAt(OffsetDateTime.now());
    }

    private void cancel(RegistrationEntity entity, UserDetailsImpl currentUser) {
        ownershipValidator.checkOwnership(entity.getParticipant().getId(), currentUser);

        RegistrationStatus current = entity.getStatus();
        if (!ACTIVE_STATUSES.contains(current)) {
            throw new BadRequestException("INVALID_STATUS_TRANSITION",
                    "Solo se puede cancelar una inscripción PENDING o CONFIRMED (actual: " + current + ")");
        }
        if (entity.getEvent().getStatus() == EventStatus.FINISHED) {
            throw new BadRequestException("EVENT_ALREADY_FINISHED",
                    "No se puede cancelar una inscripción de un evento que ya finalizó");
        }

        if (current == RegistrationStatus.CONFIRMED) {
            EventEntity event = entity.getEvent();
            event.setAvailableCapacity(event.getAvailableCapacity() + 1);
            eventRepository.save(event);
        }

        entity.setStatus(RegistrationStatus.CANCELLED);
        entity.setCancelledAt(OffsetDateTime.now());
        entity.setStatusUpdatedAt(OffsetDateTime.now());
    }

    private void requireTransitionFrom(RegistrationEntity entity, RegistrationStatus requiredFrom,
                                        RegistrationStatus target) {
        if (entity.getStatus() != requiredFrom) {
            throw new BadRequestException("INVALID_STATUS_TRANSITION",
                    "No se puede pasar de " + entity.getStatus() + " a " + target);
        }
    }

    private RegistrationEntity findEntityOrThrow(Long id) {
        return registrationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("REGISTRATION_NOT_FOUND", "Inscripción no encontrada"));
    }

    private RegistrationEntity saveAndRefresh(RegistrationEntity entity) {
        RegistrationEntity saved = registrationRepository.saveAndFlush(entity);
        entityManager.refresh(saved);
        return saved;
    }
}
