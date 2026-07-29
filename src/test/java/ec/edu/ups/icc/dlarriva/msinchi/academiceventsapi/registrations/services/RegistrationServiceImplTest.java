package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.services;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.audit.services.AuditService;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain.BadRequestException;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain.ConflictException;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain.ForbiddenException;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.entities.EventEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.enums.EventModality;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.enums.EventStatus;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.repositories.EventRepository;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.dtos.CreateRegistrationDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.dtos.RegistrationResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.dtos.UpdateRegistrationStatusDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.entities.RegistrationEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.enums.RegistrationStatus;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceImplTest {

    private static final Long EVENT_ID = 100L;
    private static final Long REGISTRATION_ID = 10L;
    private static final Long ORGANIZER_ID = 1L;
    private static final Long PARTICIPANT_ID = 5L;

    @Mock
    private RegistrationRepository registrationRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EntityManager entityManager;
    @Mock
    private AuditService auditService;

    private final OwnershipValidator ownershipValidator = new OwnershipValidatorImpl();

    private RegistrationServiceImpl registrationService;

    @BeforeEach
    void setUp() {
        registrationService = new RegistrationServiceImpl(registrationRepository, eventRepository, userRepository,
                ownershipValidator, entityManager, auditService);
    }

    // ---------------------------------------------------------------
    // create: cupo, ventana de inscripción, doble inscripción, reapertura
    // ---------------------------------------------------------------

    @Test
    void createSucceedsAsPendingWithoutTouchingEventCapacity() {
        EventEntity event = publishedEventWithCapacity(30, 30);
        when(eventRepository.findByIdAndDeletedFalse(EVENT_ID)).thenReturn(Optional.of(event));
        when(userRepository.findById(PARTICIPANT_ID)).thenReturn(Optional.of(participantEntity()));
        when(registrationRepository.findByEvent_IdAndParticipant_Id(EVENT_ID, PARTICIPANT_ID))
                .thenReturn(Optional.empty());
        when(registrationRepository.saveAndFlush(any(RegistrationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RegistrationResponseDto response = registrationService.create(createDto(), principal(PARTICIPANT_ID, RoleName.PARTICIPANT));

        assertEquals(RegistrationStatus.PENDING, response.status());
        assertEquals(30, event.getAvailableCapacity());
        verify(eventRepository, never()).save(any());
    }

    @Test
    void createRejectsWhenEventIsNotPublished() {
        EventEntity event = publishedEventWithCapacity(30, 30);
        event.setStatus(EventStatus.DRAFT);
        when(eventRepository.findByIdAndDeletedFalse(EVENT_ID)).thenReturn(Optional.of(event));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> registrationService.create(createDto(), principal(PARTICIPANT_ID, RoleName.PARTICIPANT)));

        assertEquals("EVENT_NOT_OPEN_FOR_REGISTRATION", ex.getCode());
        verify(registrationRepository, never()).saveAndFlush(any());
    }

    @Test
    void createRejectsWhenEventHasNoAvailableCapacity() {
        EventEntity event = publishedEventWithCapacity(30, 0);
        when(eventRepository.findByIdAndDeletedFalse(EVENT_ID)).thenReturn(Optional.of(event));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> registrationService.create(createDto(), principal(PARTICIPANT_ID, RoleName.PARTICIPANT)));

        assertEquals("EVENT_NO_CAPACITY", ex.getCode());
    }

    @Test
    void createRejectsDoubleActiveRegistration() {
        EventEntity event = publishedEventWithCapacity(30, 30);
        when(eventRepository.findByIdAndDeletedFalse(EVENT_ID)).thenReturn(Optional.of(event));
        when(userRepository.findById(PARTICIPANT_ID)).thenReturn(Optional.of(participantEntity()));

        RegistrationEntity existingPending = registrationOf(event, RegistrationStatus.PENDING);
        when(registrationRepository.findByEvent_IdAndParticipant_Id(EVENT_ID, PARTICIPANT_ID))
                .thenReturn(Optional.of(existingPending));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> registrationService.create(createDto(), principal(PARTICIPANT_ID, RoleName.PARTICIPANT)));

        assertEquals("REGISTRATION_ALREADY_ACTIVE", ex.getCode());
        verify(registrationRepository, never()).saveAndFlush(any());
    }

    @Test
    void createReopensCancelledRegistrationInsteadOfInserting() {
        EventEntity event = publishedEventWithCapacity(30, 30);
        when(eventRepository.findByIdAndDeletedFalse(EVENT_ID)).thenReturn(Optional.of(event));
        when(userRepository.findById(PARTICIPANT_ID)).thenReturn(Optional.of(participantEntity()));

        RegistrationEntity existingCancelled = registrationOf(event, RegistrationStatus.CANCELLED);
        existingCancelled.setCancelledAt(OffsetDateTime.now().minusDays(1));
        when(registrationRepository.findByEvent_IdAndParticipant_Id(EVENT_ID, PARTICIPANT_ID))
                .thenReturn(Optional.of(existingCancelled));
        when(registrationRepository.saveAndFlush(any(RegistrationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RegistrationResponseDto response = registrationService.create(createDto(), principal(PARTICIPANT_ID, RoleName.PARTICIPANT));

        assertEquals(RegistrationStatus.PENDING, response.status());
        assertEquals(REGISTRATION_ID, response.id());
        assertNull(existingCancelled.getCancelledAt());
    }

    @Test
    void createReopensRejectedRegistrationInsteadOfInserting() {
        EventEntity event = publishedEventWithCapacity(30, 30);
        when(eventRepository.findByIdAndDeletedFalse(EVENT_ID)).thenReturn(Optional.of(event));
        when(userRepository.findById(PARTICIPANT_ID)).thenReturn(Optional.of(participantEntity()));

        RegistrationEntity existingRejected = registrationOf(event, RegistrationStatus.REJECTED);
        when(registrationRepository.findByEvent_IdAndParticipant_Id(EVENT_ID, PARTICIPANT_ID))
                .thenReturn(Optional.of(existingRejected));
        when(registrationRepository.saveAndFlush(any(RegistrationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RegistrationResponseDto response = registrationService.create(createDto(), principal(PARTICIPANT_ID, RoleName.PARTICIPANT));

        assertEquals(RegistrationStatus.PENDING, response.status());
    }

    // ---------------------------------------------------------------
    // updateStatus -> CONFIRMED: ownership, transición, cupo, carrera
    // ---------------------------------------------------------------

    @Test
    void confirmDiscountsEventCapacityAndSetsConfirmedAt() {
        EventEntity event = publishedEventWithCapacity(30, 12);
        RegistrationEntity registration = registrationOf(event, RegistrationStatus.PENDING);
        when(registrationRepository.findById(REGISTRATION_ID)).thenReturn(Optional.of(registration));
        when(registrationRepository.saveAndFlush(any(RegistrationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RegistrationResponseDto response = registrationService.updateStatus(REGISTRATION_ID,
                statusDto(RegistrationStatus.CONFIRMED), principal(ORGANIZER_ID, RoleName.ORGANIZER));

        assertEquals(RegistrationStatus.CONFIRMED, response.status());
        assertEquals(11, event.getAvailableCapacity());
        verify(eventRepository).save(event);
    }

    @Test
    void confirmRejectsWhenCurrentUserIsNotEventOwnerNorAdmin() {
        EventEntity event = publishedEventWithCapacity(30, 12);
        RegistrationEntity registration = registrationOf(event, RegistrationStatus.PENDING);
        when(registrationRepository.findById(REGISTRATION_ID)).thenReturn(Optional.of(registration));

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> registrationService.updateStatus(REGISTRATION_ID, statusDto(RegistrationStatus.CONFIRMED),
                        principal(999L, RoleName.ORGANIZER)));

        assertEquals("NOT_RESOURCE_OWNER", ex.getCode());
        verify(eventRepository, never()).save(any());
    }

    @Test
    void confirmAllowsAdminEvenWhenNotTheEventOwner() {
        EventEntity event = publishedEventWithCapacity(30, 12);
        RegistrationEntity registration = registrationOf(event, RegistrationStatus.PENDING);
        when(registrationRepository.findById(REGISTRATION_ID)).thenReturn(Optional.of(registration));
        when(registrationRepository.saveAndFlush(any(RegistrationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> registrationService.updateStatus(REGISTRATION_ID,
                statusDto(RegistrationStatus.CONFIRMED), principal(999L, RoleName.ADMIN)));
    }

    @Test
    void confirmRejectsWhenNoCapacityLeftAtConfirmationTime() {
        EventEntity event = publishedEventWithCapacity(30, 0);
        RegistrationEntity registration = registrationOf(event, RegistrationStatus.PENDING);
        when(registrationRepository.findById(REGISTRATION_ID)).thenReturn(Optional.of(registration));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> registrationService.updateStatus(REGISTRATION_ID, statusDto(RegistrationStatus.CONFIRMED),
                        principal(ORGANIZER_ID, RoleName.ORGANIZER)));

        assertEquals("EVENT_NO_CAPACITY", ex.getCode());
        verify(registrationRepository, never()).saveAndFlush(any());
    }

    @Test
    void confirmRejectsWhenRegistrationIsNotPending() {
        EventEntity event = publishedEventWithCapacity(30, 12);
        RegistrationEntity registration = registrationOf(event, RegistrationStatus.CONFIRMED);
        when(registrationRepository.findById(REGISTRATION_ID)).thenReturn(Optional.of(registration));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> registrationService.updateStatus(REGISTRATION_ID, statusDto(RegistrationStatus.CONFIRMED),
                        principal(ORGANIZER_ID, RoleName.ORGANIZER)));

        assertEquals("INVALID_STATUS_TRANSITION", ex.getCode());
    }

    // ---------------------------------------------------------------
    // updateStatus -> REJECTED
    // ---------------------------------------------------------------

    @Test
    void rejectRequiresEventOwnerOrAdminAndDoesNotTouchCapacity() {
        EventEntity event = publishedEventWithCapacity(30, 12);
        RegistrationEntity registration = registrationOf(event, RegistrationStatus.PENDING);
        when(registrationRepository.findById(REGISTRATION_ID)).thenReturn(Optional.of(registration));
        when(registrationRepository.saveAndFlush(any(RegistrationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RegistrationResponseDto response = registrationService.updateStatus(REGISTRATION_ID,
                statusDto(RegistrationStatus.REJECTED), principal(ORGANIZER_ID, RoleName.ORGANIZER));

        assertEquals(RegistrationStatus.REJECTED, response.status());
        assertEquals(12, event.getAvailableCapacity());
        verify(eventRepository, never()).save(any());
    }

    // ---------------------------------------------------------------
    // updateStatus -> CANCELLED: ownership, devolución de cupo, plazo
    // ---------------------------------------------------------------

    @Test
    void cancelConfirmedRegistrationReturnsCapacity() {
        EventEntity event = publishedEventWithCapacity(30, 11);
        RegistrationEntity registration = registrationOf(event, RegistrationStatus.CONFIRMED);
        when(registrationRepository.findById(REGISTRATION_ID)).thenReturn(Optional.of(registration));
        when(registrationRepository.saveAndFlush(any(RegistrationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RegistrationResponseDto response = registrationService.updateStatus(REGISTRATION_ID,
                statusDto(RegistrationStatus.CANCELLED), principal(PARTICIPANT_ID, RoleName.PARTICIPANT));

        assertEquals(RegistrationStatus.CANCELLED, response.status());
        assertEquals(12, event.getAvailableCapacity());
        verify(eventRepository).save(event);
    }

    @Test
    void cancelPendingRegistrationDoesNotTouchCapacity() {
        EventEntity event = publishedEventWithCapacity(30, 12);
        RegistrationEntity registration = registrationOf(event, RegistrationStatus.PENDING);
        when(registrationRepository.findById(REGISTRATION_ID)).thenReturn(Optional.of(registration));
        when(registrationRepository.saveAndFlush(any(RegistrationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        registrationService.updateStatus(REGISTRATION_ID, statusDto(RegistrationStatus.CANCELLED),
                principal(PARTICIPANT_ID, RoleName.PARTICIPANT));

        assertEquals(12, event.getAvailableCapacity());
        verify(eventRepository, never()).save(any());
    }

    @Test
    void cancelRejectsWhenCurrentUserIsNotTheOwningParticipantNorAdmin() {
        EventEntity event = publishedEventWithCapacity(30, 12);
        RegistrationEntity registration = registrationOf(event, RegistrationStatus.PENDING);
        when(registrationRepository.findById(REGISTRATION_ID)).thenReturn(Optional.of(registration));

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> registrationService.updateStatus(REGISTRATION_ID, statusDto(RegistrationStatus.CANCELLED),
                        principal(999L, RoleName.PARTICIPANT)));

        assertEquals("NOT_RESOURCE_OWNER", ex.getCode());
    }

    @Test
    void cancelRejectsWhenEventAlreadyFinished() {
        EventEntity event = publishedEventWithCapacity(30, 12);
        event.setStatus(EventStatus.FINISHED);
        RegistrationEntity registration = registrationOf(event, RegistrationStatus.PENDING);
        when(registrationRepository.findById(REGISTRATION_ID)).thenReturn(Optional.of(registration));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> registrationService.updateStatus(REGISTRATION_ID, statusDto(RegistrationStatus.CANCELLED),
                        principal(PARTICIPANT_ID, RoleName.PARTICIPANT)));

        assertEquals("EVENT_ALREADY_FINISHED", ex.getCode());
        verify(eventRepository, never()).save(any());
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private EventEntity publishedEventWithCapacity(int capacity, int availableCapacity) {
        UserEntity organizer = new UserEntity();
        organizer.setId(ORGANIZER_ID);

        EventEntity event = new EventEntity();
        event.setId(EVENT_ID);
        event.setTitle("Evento con inscripciones");
        event.setDescription("Descripción");
        event.setModality(EventModality.VIRTUAL);
        event.setVirtualUrl("https://meet.example.test/evento");
        event.setCapacity(capacity);
        event.setAvailableCapacity(availableCapacity);
        event.setRegistrationStartAt(OffsetDateTime.now().minusDays(1));
        event.setRegistrationEndAt(OffsetDateTime.now().plusDays(5));
        event.setStartAt(OffsetDateTime.now().plusDays(10));
        event.setEndAt(OffsetDateTime.now().plusDays(10).plusHours(4));
        event.setStatus(EventStatus.PUBLISHED);
        event.setOrganizer(organizer);
        return event;
    }

    private RegistrationEntity registrationOf(EventEntity event, RegistrationStatus status) {
        RegistrationEntity entity = new RegistrationEntity();
        entity.setId(REGISTRATION_ID);
        entity.setRegistrationCode(UUID.randomUUID());
        entity.setEvent(event);
        entity.setParticipant(participantEntity());
        entity.setStatus(status);
        if (status == RegistrationStatus.CONFIRMED) {
            entity.setConfirmedAt(OffsetDateTime.now().minusDays(1));
        }
        return entity;
    }

    private UserEntity participantEntity() {
        UserEntity participant = new UserEntity();
        participant.setId(PARTICIPANT_ID);
        return participant;
    }

    private CreateRegistrationDto createDto() {
        CreateRegistrationDto dto = new CreateRegistrationDto();
        dto.setEventId(EVENT_ID);
        return dto;
    }

    private UpdateRegistrationStatusDto statusDto(RegistrationStatus status) {
        UpdateRegistrationStatusDto dto = new UpdateRegistrationStatusDto();
        dto.setStatus(status);
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
