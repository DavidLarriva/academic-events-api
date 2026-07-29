package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.services;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.audit.services.AuditService;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.categories.entities.CategoryEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.categories.repositories.CategoryRepository;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain.BadRequestException;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain.ConflictException;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain.ForbiddenException;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.dtos.CreateEventDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.dtos.EventResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.dtos.UpdateEventDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.entities.EventEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.enums.EventModality;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.enums.EventStatus;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.repositories.EventRepository;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.repositories.RegistrationRepository;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.entities.RoleEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.enums.RoleName;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services.OwnershipValidator;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services.OwnershipValidatorImpl;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services.UserDetailsImpl;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.users.entities.UserEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.users.enums.UserStatus;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.users.repositories.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceImplTest {

    @Mock
    private EventRepository eventRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RegistrationRepository registrationRepository;
    @Mock
    private EntityManager entityManager;
    @Mock
    private AuditService auditService;

    private final OwnershipValidator ownershipValidator = new OwnershipValidatorImpl();

    private EventServiceImpl eventService;

    @BeforeEach
    void setUp() {
        eventService = new EventServiceImpl(eventRepository, categoryRepository, userRepository,
                registrationRepository, ownershipValidator, entityManager, auditService);
    }

    @Test
    void createRejectsPresentialModalityWithoutLocation() {
        CreateEventDto dto = validCreateDto();
        dto.setModality(EventModality.PRESENTIAL);
        dto.setLocation(null);
        dto.setVirtualUrl(null);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> eventService.create(dto, principal(1L, RoleName.ORGANIZER)));

        assertEquals("EVENT_LOCATION_REQUIRED", ex.getCode());
        verifyNoInteractions(eventRepository);
    }

    @Test
    void createRejectsVirtualModalityWithLocationSet() {
        CreateEventDto dto = validCreateDto();
        dto.setModality(EventModality.VIRTUAL);
        dto.setLocation("Auditorio Principal");
        dto.setVirtualUrl("https://meet.example.test/session");

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> eventService.create(dto, principal(1L, RoleName.ORGANIZER)));

        assertEquals("EVENT_LOCATION_NOT_ALLOWED", ex.getCode());
    }

    @Test
    void createRejectsHybridModalityMissingVirtualUrl() {
        CreateEventDto dto = validCreateDto();
        dto.setModality(EventModality.HYBRID);
        dto.setLocation("Auditorio Principal");
        dto.setVirtualUrl(null);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> eventService.create(dto, principal(1L, RoleName.ORGANIZER)));

        assertEquals("EVENT_VIRTUAL_URL_REQUIRED", ex.getCode());
    }

    @Test
    void createSucceedsAndAvailableCapacityStartsEqualToCapacity() {
        CreateEventDto dto = validCreateDto();
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(activeCategory(2L)));

        UserEntity organizer = new UserEntity();
        organizer.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(organizer));

        when(eventRepository.saveAndFlush(any(EventEntity.class))).thenAnswer(invocation -> {
            EventEntity entity = invocation.getArgument(0);
            entity.setId(10L);
            return entity;
        });

        EventResponseDto response = eventService.create(dto, principal(1L, RoleName.ORGANIZER));

        assertEquals(dto.getCapacity(), response.availableCapacity());
        assertEquals(EventStatus.DRAFT, response.status());
        verify(entityManager).refresh(any(EventEntity.class));
    }

    @Test
    void updateRejectsWhenCurrentUserIsNotOwnerNorAdmin() {
        EventEntity existing = eventOwnedBy(5L);
        when(eventRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(existing));

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> eventService.update(10L, validUpdateDto(), principal(999L, RoleName.ORGANIZER)));

        assertEquals("NOT_RESOURCE_OWNER", ex.getCode());
        verify(eventRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateAllowsAdminEvenWhenNotTheOrganizer() {
        EventEntity existing = eventOwnedBy(5L);
        when(eventRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(activeCategory(2L)));
        when(eventRepository.saveAndFlush(any(EventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> eventService.update(10L, validUpdateDto(), principal(999L, RoleName.ADMIN)));
    }

    @Test
    void updateAllowsTheOwningOrganizer() {
        EventEntity existing = eventOwnedBy(1L);
        when(eventRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(activeCategory(2L)));
        when(eventRepository.saveAndFlush(any(EventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> eventService.update(10L, validUpdateDto(), principal(1L, RoleName.ORGANIZER)));
    }

    @Test
    void deleteRejectsPublishedEventWithActiveRegistrations() {
        EventEntity existing = eventOwnedBy(1L);
        existing.setStatus(EventStatus.PUBLISHED);
        when(eventRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(existing));
        when(registrationRepository.existsByEvent_IdAndStatusIn(eq(10L), any())).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> eventService.delete(10L, principal(1L, RoleName.ORGANIZER)));

        assertEquals("EVENT_HAS_ACTIVE_REGISTRATIONS", ex.getCode());
        verify(eventRepository, never()).save(any());
    }

    @Test
    void deleteAllowsPublishedEventWithoutActiveRegistrations() {
        EventEntity existing = eventOwnedBy(1L);
        existing.setStatus(EventStatus.PUBLISHED);
        when(eventRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(existing));
        when(registrationRepository.existsByEvent_IdAndStatusIn(eq(10L), any())).thenReturn(false);

        eventService.delete(10L, principal(1L, RoleName.ORGANIZER));

        assertTrue(existing.isDeleted());
        verify(eventRepository).save(existing);
    }

    @Test
    void deleteAllowsDraftEventEvenWithRegistrations() {
        EventEntity existing = eventOwnedBy(1L);
        existing.setStatus(EventStatus.DRAFT);
        when(eventRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(existing));

        eventService.delete(10L, principal(1L, RoleName.ORGANIZER));

        assertTrue(existing.isDeleted());
        verify(registrationRepository, never()).existsByEvent_IdAndStatusIn(any(), any());
    }

    private CreateEventDto validCreateDto() {
        CreateEventDto dto = new CreateEventDto();
        dto.setTitle("Taller de Spring Boot");
        dto.setDescription("Construcción de una API REST segura");
        dto.setModality(EventModality.PRESENTIAL);
        dto.setLocation("Laboratorio de Computación 3");
        dto.setVirtualUrl(null);
        dto.setCapacity(30);
        dto.setRegistrationStartAt(OffsetDateTime.now());
        dto.setRegistrationEndAt(OffsetDateTime.now().plusDays(5));
        dto.setStartAt(OffsetDateTime.now().plusDays(10));
        dto.setEndAt(OffsetDateTime.now().plusDays(10).plusHours(4));
        dto.setCategoryId(2L);
        return dto;
    }

    private UpdateEventDto validUpdateDto() {
        UpdateEventDto dto = new UpdateEventDto();
        dto.setTitle("Taller de Spring Boot (actualizado)");
        dto.setDescription("Construcción de una API REST segura, edición 2");
        dto.setModality(EventModality.PRESENTIAL);
        dto.setLocation("Laboratorio de Computación 3");
        dto.setVirtualUrl(null);
        dto.setCapacity(30);
        dto.setRegistrationStartAt(OffsetDateTime.now());
        dto.setRegistrationEndAt(OffsetDateTime.now().plusDays(5));
        dto.setStartAt(OffsetDateTime.now().plusDays(10));
        dto.setEndAt(OffsetDateTime.now().plusDays(10).plusHours(4));
        dto.setStatus(EventStatus.DRAFT);
        dto.setCategoryId(2L);
        return dto;
    }

    private EventEntity eventOwnedBy(Long organizerId) {
        UserEntity organizer = new UserEntity();
        organizer.setId(organizerId);

        CategoryEntity category = activeCategory(2L);

        EventEntity entity = new EventEntity();
        entity.setId(10L);
        entity.setTitle("Evento existente");
        entity.setDescription("Descripción");
        entity.setModality(EventModality.PRESENTIAL);
        entity.setLocation("Auditorio Principal");
        entity.setCapacity(30);
        entity.setAvailableCapacity(30);
        entity.setRegistrationStartAt(OffsetDateTime.now());
        entity.setRegistrationEndAt(OffsetDateTime.now().plusDays(5));
        entity.setStartAt(OffsetDateTime.now().plusDays(10));
        entity.setEndAt(OffsetDateTime.now().plusDays(10).plusHours(4));
        entity.setStatus(EventStatus.DRAFT);
        entity.setOrganizer(organizer);
        entity.setCategory(category);
        return entity;
    }

    private CategoryEntity activeCategory(Long id) {
        CategoryEntity category = new CategoryEntity();
        category.setId(id);
        category.setName("Desarrollo de Software");
        category.setActive(true);
        return category;
    }

    private UserDetailsImpl principal(Long id, RoleName roleName) {
        RoleEntity role = new RoleEntity();
        role.setId(1L);
        role.setName(roleName);
        role.setDescription(roleName.name());

        UserEntity user = new UserEntity();
        user.setId(id);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEmail("test" + id + "@academic.test");
        user.setPasswordHash("hash");
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of(role));

        return UserDetailsImpl.build(user);
    }
}
