package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.services;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.audit.services.AuditService;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.categories.entities.CategoryEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.categories.repositories.CategoryRepository;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.dtos.PagedResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.dtos.PaginationDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain.BadRequestException;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain.ConflictException;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain.NotFoundException;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.dtos.CreateEventDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.dtos.EventFilterDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.dtos.EventResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.dtos.UpdateEventDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.entities.EventEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.enums.EventModality;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.enums.EventStatus;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.mappers.EventMapper;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.repositories.EventRepository;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.enums.RegistrationStatus;
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
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Replica en Bean Validation/service los CHECK de la tabla events
 * (V1__initial_schema_and_data.sql) para dar 400 legibles en vez de dejar
 * que rompa el constraint: capacity>0 vía @Min en el DTO; el resto
 * (modalidad↔location/virtualUrl, orden de fechas) se valida acá porque son
 * reglas cruzadas entre campos. "deleted" (no "active") es la eliminación
 * lógica de events, y organizerId sale siempre de currentUser
 * (@AuthenticationPrincipal), nunca del body.
 */
@Service
public class EventServiceImpl implements EventService {

    private static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of("id", "title", "startAt", "endAt", "status", "capacity", "createdAt", "updatedAt");
    private static final List<RegistrationStatus> ACTIVE_REGISTRATION_STATUSES =
            List.of(RegistrationStatus.PENDING, RegistrationStatus.CONFIRMED);

    private final EventRepository eventRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final RegistrationRepository registrationRepository;
    private final OwnershipValidator ownershipValidator;
    private final EntityManager entityManager;
    private final AuditService auditService;

    public EventServiceImpl(EventRepository eventRepository, CategoryRepository categoryRepository,
                             UserRepository userRepository, RegistrationRepository registrationRepository,
                             OwnershipValidator ownershipValidator, EntityManager entityManager,
                             AuditService auditService) {
        this.eventRepository = eventRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.registrationRepository = registrationRepository;
        this.ownershipValidator = ownershipValidator;
        this.entityManager = entityManager;
        this.auditService = auditService;
    }

    @Override
    public PagedResponseDto<EventResponseDto> findPage(EventFilterDto filters, PaginationDto pagination) {
        Pageable pageable = pagination.toPageable(ALLOWED_SORT_FIELDS);
        Page<EventEntity> page = eventRepository.search(filters.getCategoryId(), filters.getStatus(),
                filters.getOrganizerId(), filters.getStartFrom(), filters.getStartTo(), pageable);
        return PagedResponseDto.of(page, entity -> EventMapper.toResponse(EventMapper.toModel(entity)));
    }

    @Override
    public EventResponseDto findOne(Long id) {
        return EventMapper.toResponse(EventMapper.toModel(findEntityOrThrow(id)));
    }

    @Override
    @Transactional
    public EventResponseDto create(CreateEventDto dto, UserDetailsImpl currentUser) {
        validateModalityData(dto.getModality(), dto.getLocation(), dto.getVirtualUrl());
        validateDateOrder(dto.getRegistrationStartAt(), dto.getRegistrationEndAt(), dto.getStartAt(), dto.getEndAt());

        CategoryEntity category = findCategoryOrThrow(dto.getCategoryId());
        validateCategoryAssignable(category, null);

        UserEntity organizer = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "Usuario no encontrado"));

        EventEntity entity = new EventEntity();
        entity.setTitle(dto.getTitle().trim());
        entity.setDescription(dto.getDescription());
        entity.setModality(dto.getModality());
        entity.setLocation(dto.getLocation());
        entity.setVirtualUrl(dto.getVirtualUrl());
        entity.setCapacity(dto.getCapacity());
        // Nace sin inscripciones: disponible = capacidad total. El descuento
        // ocurre cuando una inscripción pasa a CONFIRMED (módulo registrations).
        entity.setAvailableCapacity(dto.getCapacity());
        entity.setRegistrationStartAt(dto.getRegistrationStartAt());
        entity.setRegistrationEndAt(dto.getRegistrationEndAt());
        entity.setStartAt(dto.getStartAt());
        entity.setEndAt(dto.getEndAt());
        entity.setStatus(EventStatus.DRAFT);
        entity.setOrganizer(organizer);
        entity.setCategory(category);

        EventEntity saved = saveAndRefresh(entity);
        auditService.recordSuccess(currentUser.getId(), "EVENT_CREATED", "EVENT", saved.getId(), null,
                Map.of("title", saved.getTitle(), "status", saved.getStatus().name()));
        return EventMapper.toResponse(EventMapper.toModel(saved));
    }

    @Override
    @Transactional
    public EventResponseDto update(Long id, UpdateEventDto dto, UserDetailsImpl currentUser) {
        EventEntity entity = findEntityOrThrow(id);
        ownershipValidator.checkOwnership(entity.getOrganizer().getId(), currentUser);

        validateModalityData(dto.getModality(), dto.getLocation(), dto.getVirtualUrl());
        validateDateOrder(dto.getRegistrationStartAt(), dto.getRegistrationEndAt(), dto.getStartAt(), dto.getEndAt());

        CategoryEntity category = findCategoryOrThrow(dto.getCategoryId());
        validateCategoryAssignable(category, entity.getCategory());

        entity.setTitle(dto.getTitle().trim());
        entity.setDescription(dto.getDescription());
        entity.setModality(dto.getModality());
        entity.setLocation(dto.getLocation());
        entity.setVirtualUrl(dto.getVirtualUrl());
        entity.setRegistrationStartAt(dto.getRegistrationStartAt());
        entity.setRegistrationEndAt(dto.getRegistrationEndAt());
        entity.setStartAt(dto.getStartAt());
        entity.setEndAt(dto.getEndAt());
        entity.setStatus(dto.getStatus());
        entity.setCategory(category);
        adjustCapacity(entity, dto.getCapacity());

        return EventMapper.toResponse(EventMapper.toModel(saveAndRefresh(entity)));
    }

    @Override
    @Transactional
    public void delete(Long id, UserDetailsImpl currentUser) {
        EventEntity entity = findEntityOrThrow(id);
        ownershipValidator.checkOwnership(entity.getOrganizer().getId(), currentUser);

        boolean isPublishedWithActiveRegistrations = entity.getStatus() == EventStatus.PUBLISHED
                && registrationRepository.existsByEvent_IdAndStatusIn(id, ACTIVE_REGISTRATION_STATUSES);
        if (isPublishedWithActiveRegistrations) {
            throw new ConflictException("EVENT_HAS_ACTIVE_REGISTRATIONS",
                    "No se puede eliminar un evento publicado con inscripciones activas");
        }

        entity.setDeleted(true);
        eventRepository.save(entity);

        auditService.recordSuccess(currentUser.getId(), "EVENT_DELETED", "EVENT", entity.getId(),
                Map.of("deleted", false), Map.of("deleted", true));
    }

    private EventEntity findEntityOrThrow(Long id) {
        return eventRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("EVENT_NOT_FOUND", "Evento no encontrado"));
    }

    private CategoryEntity findCategoryOrThrow(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("CATEGORY_NOT_FOUND", "Categoría no encontrada"));
    }

    /**
     * "Una categoría con active=false no debería poder usarse en eventos
     * nuevos" (prompt de categories): se permite solo si es la MISMA
     * categoría que el evento ya tenía (no bloquea ediciones que no tocan
     * la categoría de un evento viejo, pero sí impide asignar una inactiva
     * nueva).
     */
    private void validateCategoryAssignable(CategoryEntity category, CategoryEntity currentCategory) {
        boolean isSameAsCurrent = currentCategory != null && currentCategory.getId().equals(category.getId());
        if (!category.isActive() && !isSameAsCurrent) {
            throw new BadRequestException("CATEGORY_INACTIVE", "No se puede asignar una categoría inactiva");
        }
    }

    private void validateModalityData(EventModality modality, String location, String virtualUrl) {
        boolean hasLocation = StringUtils.hasText(location);
        boolean hasVirtualUrl = StringUtils.hasText(virtualUrl);
        switch (modality) {
            case PRESENTIAL -> {
                requireField(hasLocation, "EVENT_LOCATION_REQUIRED", "La modalidad PRESENTIAL requiere location");
                rejectField(hasVirtualUrl, "EVENT_VIRTUAL_URL_NOT_ALLOWED",
                        "La modalidad PRESENTIAL no admite virtualUrl");
            }
            case VIRTUAL -> {
                rejectField(hasLocation, "EVENT_LOCATION_NOT_ALLOWED", "La modalidad VIRTUAL no admite location");
                requireField(hasVirtualUrl, "EVENT_VIRTUAL_URL_REQUIRED", "La modalidad VIRTUAL requiere virtualUrl");
            }
            case HYBRID -> {
                requireField(hasLocation, "EVENT_LOCATION_REQUIRED", "La modalidad HYBRID requiere location");
                requireField(hasVirtualUrl, "EVENT_VIRTUAL_URL_REQUIRED", "La modalidad HYBRID requiere virtualUrl");
            }
        }
    }

    private void requireField(boolean present, String code, String message) {
        if (!present) {
            throw new BadRequestException(code, message);
        }
    }

    private void rejectField(boolean present, String code, String message) {
        if (present) {
            throw new BadRequestException(code, message);
        }
    }

    private void validateDateOrder(OffsetDateTime registrationStart, OffsetDateTime registrationEnd,
                                    OffsetDateTime start, OffsetDateTime end) {
        if (!registrationStart.isBefore(registrationEnd)) {
            throw new BadRequestException("EVENT_INVALID_DATES",
                    "registrationStartAt debe ser anterior a registrationEndAt");
        }
        if (registrationEnd.isAfter(start)) {
            throw new BadRequestException("EVENT_INVALID_DATES",
                    "registrationEndAt debe ser menor o igual a startAt");
        }
        if (!start.isBefore(end)) {
            throw new BadRequestException("EVENT_INVALID_DATES", "startAt debe ser anterior a endAt");
        }
    }

    /**
     * available_capacity solo se descuenta cuando una inscripción pasa a
     * CONFIRMED (módulo registrations), pero si el organizador cambia la
     * capacidad total acá, hay que preservar los cupos ya consumidos en vez
     * de simplemente igualar disponible=nueva capacidad.
     */
    private void adjustCapacity(EventEntity entity, Integer newCapacity) {
        int consumed = entity.getCapacity() - entity.getAvailableCapacity();
        int newAvailable = newCapacity - consumed;
        if (newAvailable < 0) {
            throw new BadRequestException("EVENT_CAPACITY_BELOW_CONFIRMED",
                    "No se puede reducir la capacidad por debajo de las inscripciones ya confirmadas (" + consumed + ")");
        }
        entity.setCapacity(newCapacity);
        entity.setAvailableCapacity(newAvailable);
    }

    private EventEntity saveAndRefresh(EventEntity entity) {
        EventEntity saved = eventRepository.saveAndFlush(entity);
        entityManager.refresh(saved);
        return saved;
    }
}
