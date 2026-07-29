package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.sessions.services;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.dtos.PaginationDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain.BadRequestException;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain.ConflictException;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain.ForbiddenException;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain.NotFoundException;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.entities.EventEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.enums.EventModality;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.enums.EventStatus;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.repositories.EventRepository;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.entities.RoleEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.enums.RoleName;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services.OwnershipValidator;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services.OwnershipValidatorImpl;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services.UserDetailsImpl;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.sessions.dtos.CreateSessionDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.sessions.dtos.SessionFilterDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.sessions.dtos.UpdateSessionDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.sessions.entities.SessionEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.sessions.repositories.SessionRepository;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.users.entities.UserEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.users.enums.UserStatus;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceImplTest {

    private static final Long EVENT_ID = 100L;
    private static final Long SESSION_ID = 10L;

    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private EntityManager entityManager;

    private final OwnershipValidator ownershipValidator = new OwnershipValidatorImpl();

    private SessionServiceImpl sessionService;

    @BeforeEach
    void setUp() {
        sessionService = new SessionServiceImpl(sessionRepository, eventRepository, ownershipValidator,
                entityManager);
    }

    // ---------------------------------------------------------------
    // Ownership vía evento padre (create)
    // ---------------------------------------------------------------

    @Test
    void createRejectsWhenCurrentUserIsNotEventOwnerNorAdmin() {
        EventEntity event = eventOwnedBy(5L);
        when(eventRepository.findByIdAndDeletedFalse(EVENT_ID)).thenReturn(Optional.of(event));

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> sessionService.create(EVENT_ID, validCreateDto(event), principal(999L, RoleName.ORGANIZER)));

        assertEquals("NOT_RESOURCE_OWNER", ex.getCode());
        verify(sessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void createAllowsTheOwningOrganizer() {
        EventEntity event = eventOwnedBy(1L);
        when(eventRepository.findByIdAndDeletedFalse(EVENT_ID)).thenReturn(Optional.of(event));
        when(sessionRepository.saveAndFlush(any(SessionEntity.class))).thenAnswer(invocation -> {
            SessionEntity entity = invocation.getArgument(0);
            entity.setId(SESSION_ID);
            return entity;
        });

        assertDoesNotThrow(() -> sessionService.create(EVENT_ID, validCreateDto(event), principal(1L, RoleName.ORGANIZER)));
        verify(sessionRepository).saveAndFlush(any(SessionEntity.class));
    }

    @Test
    void createAllowsAdminEvenWhenNotTheEventOwner() {
        EventEntity event = eventOwnedBy(5L);
        when(eventRepository.findByIdAndDeletedFalse(EVENT_ID)).thenReturn(Optional.of(event));
        when(sessionRepository.saveAndFlush(any(SessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> sessionService.create(EVENT_ID, validCreateDto(event), principal(999L, RoleName.ADMIN)));
    }

    @Test
    void createFailsWhenParentEventDoesNotExist() {
        when(eventRepository.findByIdAndDeletedFalse(EVENT_ID)).thenReturn(Optional.empty());

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> sessionService.create(EVENT_ID, validCreateDto(eventOwnedBy(1L)), principal(1L, RoleName.ORGANIZER)));

        assertEquals("EVENT_NOT_FOUND", ex.getCode());
    }

    // ---------------------------------------------------------------
    // Ownership vía evento padre (update)
    // ---------------------------------------------------------------

    @Test
    void updateRejectsWhenCurrentUserIsNotEventOwnerNorAdmin() {
        EventEntity event = eventOwnedBy(5L);
        SessionEntity existing = sessionOf(event, SESSION_ID);
        when(sessionRepository.findByIdAndEvent_Id(SESSION_ID, EVENT_ID)).thenReturn(Optional.of(existing));

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> sessionService.update(EVENT_ID, SESSION_ID, validUpdateDto(event), principal(999L, RoleName.ORGANIZER)));

        assertEquals("NOT_RESOURCE_OWNER", ex.getCode());
        verify(sessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateAllowsTheOwningOrganizer() {
        EventEntity event = eventOwnedBy(1L);
        SessionEntity existing = sessionOf(event, SESSION_ID);
        when(sessionRepository.findByIdAndEvent_Id(SESSION_ID, EVENT_ID)).thenReturn(Optional.of(existing));
        when(sessionRepository.saveAndFlush(any(SessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> sessionService.update(EVENT_ID, SESSION_ID, validUpdateDto(event), principal(1L, RoleName.ORGANIZER)));
    }

    @Test
    void updateAllowsAdminEvenWhenNotTheEventOwner() {
        EventEntity event = eventOwnedBy(5L);
        SessionEntity existing = sessionOf(event, SESSION_ID);
        when(sessionRepository.findByIdAndEvent_Id(SESSION_ID, EVENT_ID)).thenReturn(Optional.of(existing));
        when(sessionRepository.saveAndFlush(any(SessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> sessionService.update(EVENT_ID, SESSION_ID, validUpdateDto(event), principal(999L, RoleName.ADMIN)));
    }

    @Test
    void updateFailsWhenSessionDoesNotBelongToEventInUrl() {
        when(sessionRepository.findByIdAndEvent_Id(SESSION_ID, EVENT_ID)).thenReturn(Optional.empty());

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> sessionService.update(EVENT_ID, SESSION_ID, validUpdateDto(eventOwnedBy(1L)), principal(1L, RoleName.ORGANIZER)));

        assertEquals("SESSION_NOT_FOUND", ex.getCode());
    }

    // ---------------------------------------------------------------
    // Ownership vía evento padre (delete)
    // ---------------------------------------------------------------

    @Test
    void deleteRejectsWhenCurrentUserIsNotEventOwnerNorAdmin() {
        EventEntity event = eventOwnedBy(5L);
        SessionEntity existing = sessionOf(event, SESSION_ID);
        when(sessionRepository.findByIdAndEvent_Id(SESSION_ID, EVENT_ID)).thenReturn(Optional.of(existing));

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> sessionService.delete(EVENT_ID, SESSION_ID, principal(999L, RoleName.ORGANIZER)));

        assertEquals("NOT_RESOURCE_OWNER", ex.getCode());
        verify(sessionRepository, never()).delete(any());
    }

    @Test
    void deleteAllowsTheOwningOrganizer() {
        EventEntity event = eventOwnedBy(1L);
        SessionEntity existing = sessionOf(event, SESSION_ID);
        when(sessionRepository.findByIdAndEvent_Id(SESSION_ID, EVENT_ID)).thenReturn(Optional.of(existing));

        sessionService.delete(EVENT_ID, SESSION_ID, principal(1L, RoleName.ORGANIZER));

        verify(sessionRepository).delete(existing);
    }

    @Test
    void deleteAllowsAdminEvenWhenNotTheEventOwner() {
        EventEntity event = eventOwnedBy(5L);
        SessionEntity existing = sessionOf(event, SESSION_ID);
        when(sessionRepository.findByIdAndEvent_Id(SESSION_ID, EVENT_ID)).thenReturn(Optional.of(existing));

        sessionService.delete(EVENT_ID, SESSION_ID, principal(999L, RoleName.ADMIN));

        verify(sessionRepository).delete(existing);
    }

    // ---------------------------------------------------------------
    // Reglas de horario (acordadas con el usuario)
    // ---------------------------------------------------------------

    @Test
    void createRejectsSessionStartingBeforeEventStart() {
        EventEntity event = eventOwnedBy(1L);
        when(eventRepository.findByIdAndDeletedFalse(EVENT_ID)).thenReturn(Optional.of(event));

        CreateSessionDto dto = validCreateDto(event);
        dto.setStartAt(event.getStartAt().minusMinutes(1));
        dto.setEndAt(event.getStartAt().plusHours(1));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> sessionService.create(EVENT_ID, dto, principal(1L, RoleName.ORGANIZER)));

        assertEquals("SESSION_OUT_OF_EVENT_RANGE", ex.getCode());
    }

    @Test
    void createRejectsSessionEndingAfterEventEnd() {
        EventEntity event = eventOwnedBy(1L);
        when(eventRepository.findByIdAndDeletedFalse(EVENT_ID)).thenReturn(Optional.of(event));

        CreateSessionDto dto = validCreateDto(event);
        dto.setStartAt(event.getEndAt().minusHours(1));
        dto.setEndAt(event.getEndAt().plusMinutes(1));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> sessionService.create(EVENT_ID, dto, principal(1L, RoleName.ORGANIZER)));

        assertEquals("SESSION_OUT_OF_EVENT_RANGE", ex.getCode());
    }

    @Test
    void createRejectsStartAtNotBeforeEndAt() {
        EventEntity event = eventOwnedBy(1L);
        when(eventRepository.findByIdAndDeletedFalse(EVENT_ID)).thenReturn(Optional.of(event));

        CreateSessionDto dto = validCreateDto(event);
        dto.setStartAt(dto.getEndAt());

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> sessionService.create(EVENT_ID, dto, principal(1L, RoleName.ORGANIZER)));

        assertEquals("SESSION_INVALID_DATES", ex.getCode());
    }

    @Test
    void createRejectsDuplicateTitleAndStartAtWithin409() {
        EventEntity event = eventOwnedBy(1L);
        when(eventRepository.findByIdAndDeletedFalse(EVENT_ID)).thenReturn(Optional.of(event));

        CreateSessionDto dto = validCreateDto(event);
        when(sessionRepository.existsByEvent_IdAndTitleAndStartAt(EVENT_ID, dto.getTitle(), dto.getStartAt()))
                .thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> sessionService.create(EVENT_ID, dto, principal(1L, RoleName.ORGANIZER)));

        assertEquals("SESSION_DUPLICATE", ex.getCode());
        verify(sessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void createRejectsOverlappingSessionWithin409() {
        EventEntity event = eventOwnedBy(1L);
        when(eventRepository.findByIdAndDeletedFalse(EVENT_ID)).thenReturn(Optional.of(event));

        CreateSessionDto dto = validCreateDto(event);
        lenient().when(sessionRepository.existsOverlapping(eq(EVENT_ID), eq(0L), any(), any())).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> sessionService.create(EVENT_ID, dto, principal(1L, RoleName.ORGANIZER)));

        assertEquals("SESSION_OVERLAPS", ex.getCode());
        verify(sessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateExcludesItselfFromDuplicateAndOverlapChecks() {
        EventEntity event = eventOwnedBy(1L);
        SessionEntity existing = sessionOf(event, SESSION_ID);
        when(sessionRepository.findByIdAndEvent_Id(SESSION_ID, EVENT_ID)).thenReturn(Optional.of(existing));
        when(sessionRepository.saveAndFlush(any(SessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateSessionDto dto = validUpdateDto(event);

        assertDoesNotThrow(() -> sessionService.update(EVENT_ID, SESSION_ID, dto, principal(1L, RoleName.ORGANIZER)));

        verify(sessionRepository).existsByEvent_IdAndTitleAndStartAtAndIdNot(EVENT_ID, dto.getTitle(), dto.getStartAt(), SESSION_ID);
        verify(sessionRepository).existsOverlapping(EVENT_ID, SESSION_ID, dto.getStartAt(), dto.getEndAt());
    }

    @Test
    void findPageValidatesParentEventExistsFirst() {
        when(eventRepository.findByIdAndDeletedFalse(EVENT_ID)).thenReturn(Optional.empty());

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> sessionService.findPage(EVENT_ID, new SessionFilterDto(), new PaginationDto()));

        assertEquals("EVENT_NOT_FOUND", ex.getCode());
        verify(sessionRepository, never()).search(anyLong(), any(), any(), any(), any());
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private EventEntity eventOwnedBy(Long organizerId) {
        UserEntity organizer = new UserEntity();
        organizer.setId(organizerId);

        EventEntity event = new EventEntity();
        event.setId(EVENT_ID);
        event.setTitle("Evento contenedor");
        event.setDescription("Descripción");
        event.setModality(EventModality.VIRTUAL);
        event.setVirtualUrl("https://meet.example.test/evento");
        event.setCapacity(50);
        event.setAvailableCapacity(50);
        event.setRegistrationStartAt(OffsetDateTime.now());
        event.setRegistrationEndAt(OffsetDateTime.now().plusDays(5));
        event.setStartAt(OffsetDateTime.now().plusDays(10));
        event.setEndAt(OffsetDateTime.now().plusDays(10).plusHours(8));
        event.setStatus(EventStatus.PUBLISHED);
        event.setOrganizer(organizer);
        return event;
    }

    private SessionEntity sessionOf(EventEntity event, Long id) {
        SessionEntity session = new SessionEntity();
        session.setId(id);
        session.setEvent(event);
        session.setTitle("Sesión existente");
        session.setDescription("Descripción de la sesión");
        session.setStartAt(event.getStartAt().plusHours(1));
        session.setEndAt(event.getStartAt().plusHours(2));
        session.setLocation(null);
        session.setVirtualUrl("https://meet.example.test/sesion");
        return session;
    }

    private CreateSessionDto validCreateDto(EventEntity event) {
        CreateSessionDto dto = new CreateSessionDto();
        dto.setTitle("Nueva sesión");
        dto.setDescription("Descripción de la nueva sesión");
        dto.setStartAt(event.getStartAt().plusHours(1));
        dto.setEndAt(event.getStartAt().plusHours(2));
        dto.setVirtualUrl("https://meet.example.test/nueva-sesion");
        return dto;
    }

    private UpdateSessionDto validUpdateDto(EventEntity event) {
        UpdateSessionDto dto = new UpdateSessionDto();
        dto.setTitle("Sesión actualizada");
        dto.setDescription("Descripción actualizada");
        dto.setStartAt(event.getStartAt().plusHours(1));
        dto.setEndAt(event.getStartAt().plusHours(3));
        dto.setVirtualUrl("https://meet.example.test/sesion-actualizada");
        return dto;
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
