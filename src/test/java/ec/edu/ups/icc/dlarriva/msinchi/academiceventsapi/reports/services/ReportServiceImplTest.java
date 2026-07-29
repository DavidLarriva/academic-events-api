package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.reports.services;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain.BadRequestException;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain.ForbiddenException;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain.NotFoundException;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.entities.EventEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.enums.EventModality;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.enums.EventStatus;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.repositories.EventRepository;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.entities.RegistrationEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.enums.RegistrationStatus;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.repositories.RegistrationRepository;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.reports.dtos.ReportFilterDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.entities.RoleEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.enums.RoleName;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services.OwnershipValidator;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services.OwnershipValidatorImpl;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services.UserDetailsImpl;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.users.entities.UserEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.users.enums.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    private static final Long EVENT_ID = 100L;

    @Mock
    private EventRepository eventRepository;
    @Mock
    private RegistrationRepository registrationRepository;

    private final OwnershipValidator ownershipValidator = new OwnershipValidatorImpl();

    private ReportServiceImpl reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportServiceImpl(eventRepository, registrationRepository, ownershipValidator);
    }

    @Test
    void generatePdfRejectsWhenCurrentUserIsNotEventOwnerNorAdmin() {
        EventEntity event = eventOwnedBy(1L);
        when(eventRepository.findByIdAndDeletedFalse(EVENT_ID)).thenReturn(Optional.of(event));

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> reportService.generateRegistrationsPdf(EVENT_ID, new ReportFilterDto(),
                        principal(999L, RoleName.ORGANIZER)));

        assertEquals("NOT_RESOURCE_OWNER", ex.getCode());
        verify(registrationRepository, never()).findForReport(any(), any(), any(), any());
    }

    @Test
    void generatePdfFailsWhenEventDoesNotExist() {
        when(eventRepository.findByIdAndDeletedFalse(EVENT_ID)).thenReturn(Optional.empty());

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> reportService.generateRegistrationsPdf(EVENT_ID, new ReportFilterDto(),
                        principal(1L, RoleName.ORGANIZER)));

        assertEquals("EVENT_NOT_FOUND", ex.getCode());
    }

    @Test
    void generatePdfAllowsTheOwningOrganizerAndProducesAValidPdf() {
        EventEntity event = eventOwnedBy(1L);
        when(eventRepository.findByIdAndDeletedFalse(EVENT_ID)).thenReturn(Optional.of(event));
        when(registrationRepository.findForReport(eq(EVENT_ID), any(), any(), any()))
                .thenReturn(List.of(registrationOf(event, "Ana", "Lucía", "ana@academic.test")));

        byte[] pdf = reportService.generateRegistrationsPdf(EVENT_ID, new ReportFilterDto(),
                principal(1L, RoleName.ORGANIZER));

        assertTrue(pdf.length > 0);
        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.US_ASCII));
    }

    @Test
    void generateExcelAllowsAdminEvenWhenNotTheEventOwnerAndProducesAValidWorkbook() {
        EventEntity event = eventOwnedBy(5L);
        when(eventRepository.findByIdAndDeletedFalse(EVENT_ID)).thenReturn(Optional.of(event));
        when(registrationRepository.findForReport(eq(EVENT_ID), any(), any(), any()))
                .thenReturn(List.of(registrationOf(event, "Ana", "Lucía", "ana@academic.test")));

        byte[] xlsx = reportService.generateRegistrationsExcel(EVENT_ID, new ReportFilterDto(),
                principal(999L, RoleName.ADMIN));

        assertTrue(xlsx.length > 0);
        // .xlsx es un ZIP: firma PK\x03\x04
        assertEquals(0x50, xlsx[0] & 0xFF);
        assertEquals(0x4B, xlsx[1] & 0xFF);
    }

    @Test
    void defaultFilterQueriesOnlyConfirmedRegistrations() {
        EventEntity event = eventOwnedBy(1L);
        when(eventRepository.findByIdAndDeletedFalse(EVENT_ID)).thenReturn(Optional.of(event));
        when(registrationRepository.findForReport(any(), any(), any(), any())).thenReturn(List.of());

        reportService.generateRegistrationsPdf(EVENT_ID, new ReportFilterDto(), principal(1L, RoleName.ORGANIZER));

        ArgumentCaptor<RegistrationStatus> statusCaptor = ArgumentCaptor.forClass(RegistrationStatus.class);
        verify(registrationRepository).findForReport(eq(EVENT_ID), statusCaptor.capture(), any(), any());
        assertEquals(RegistrationStatus.CONFIRMED, statusCaptor.getValue());
    }

    @Test
    void explicitStatusFilterOverridesTheConfirmedDefault() {
        EventEntity event = eventOwnedBy(1L);
        when(eventRepository.findByIdAndDeletedFalse(EVENT_ID)).thenReturn(Optional.of(event));
        when(registrationRepository.findForReport(any(), any(), any(), any())).thenReturn(List.of());

        ReportFilterDto filters = new ReportFilterDto();
        filters.setStatus(RegistrationStatus.PENDING);

        reportService.generateRegistrationsExcel(EVENT_ID, filters, principal(1L, RoleName.ORGANIZER));

        verify(registrationRepository).findForReport(eq(EVENT_ID), eq(RegistrationStatus.PENDING), any(), any());
    }

    // ---------------------------------------------------------------
    // generateRegistrationCertificate: solo el PARTICIPANT dueño, sin
    // excepción para ADMIN (a diferencia de los reportes de arriba).
    // ---------------------------------------------------------------

    @Test
    void certificateFailsWhenRegistrationDoesNotExist() {
        when(registrationRepository.findByIdWithEventAndParticipant(1L)).thenReturn(Optional.empty());

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> reportService.generateRegistrationCertificate(1L, principal(16L, RoleName.PARTICIPANT)));

        assertEquals("REGISTRATION_NOT_FOUND", ex.getCode());
    }

    @Test
    void certificateRejectsWhenCurrentUserIsNotTheOwningParticipant() {
        EventEntity event = eventOwnedBy(1L);
        RegistrationEntity registration = registrationOf(event, "Ana", "Lucía", "ana@academic.test");
        when(registrationRepository.findByIdWithEventAndParticipant(1L)).thenReturn(Optional.of(registration));

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> reportService.generateRegistrationCertificate(1L, principal(999L, RoleName.PARTICIPANT)));

        assertEquals("NOT_RESOURCE_OWNER", ex.getCode());
    }

    @Test
    void certificateRejectsEvenForAdminWhenNotTheOwningParticipant() {
        // A propósito NO reutiliza OwnershipValidator (que siempre deja pasar
        // a ADMIN): este comprobante es exclusivo del participante dueño.
        EventEntity event = eventOwnedBy(1L);
        RegistrationEntity registration = registrationOf(event, "Ana", "Lucía", "ana@academic.test");
        when(registrationRepository.findByIdWithEventAndParticipant(1L)).thenReturn(Optional.of(registration));

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> reportService.generateRegistrationCertificate(1L, principal(999L, RoleName.ADMIN)));

        assertEquals("NOT_RESOURCE_OWNER", ex.getCode());
    }

    @Test
    void certificateRejectsWhenRegistrationIsNotConfirmed() {
        EventEntity event = eventOwnedBy(1L);
        RegistrationEntity registration = registrationOf(event, "Ana", "Lucía", "ana@academic.test");
        registration.setStatus(RegistrationStatus.PENDING);
        when(registrationRepository.findByIdWithEventAndParticipant(1L)).thenReturn(Optional.of(registration));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> reportService.generateRegistrationCertificate(1L, principal(16L, RoleName.PARTICIPANT)));

        assertEquals("REGISTRATION_NOT_CONFIRMED", ex.getCode());
    }

    @Test
    void certificateSucceedsForTheOwningParticipantAndProducesAValidPdf() {
        EventEntity event = eventOwnedBy(1L);
        RegistrationEntity registration = registrationOf(event, "Ana", "Lucía", "ana@academic.test");
        when(registrationRepository.findByIdWithEventAndParticipant(1L)).thenReturn(Optional.of(registration));

        byte[] pdf = reportService.generateRegistrationCertificate(1L, principal(16L, RoleName.PARTICIPANT));

        assertTrue(pdf.length > 0);
        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.US_ASCII));
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private EventEntity eventOwnedBy(Long organizerId) {
        UserEntity organizer = new UserEntity();
        organizer.setId(organizerId);

        EventEntity event = new EventEntity();
        event.setId(EVENT_ID);
        event.setTitle("Evento con inscripciones");
        event.setDescription("Descripción");
        event.setModality(EventModality.VIRTUAL);
        event.setVirtualUrl("https://meet.example.test/evento");
        event.setCapacity(30);
        event.setAvailableCapacity(30);
        event.setRegistrationStartAt(OffsetDateTime.now().minusDays(1));
        event.setRegistrationEndAt(OffsetDateTime.now().plusDays(5));
        event.setStartAt(OffsetDateTime.now().plusDays(10));
        event.setEndAt(OffsetDateTime.now().plusDays(10).plusHours(4));
        event.setStatus(EventStatus.PUBLISHED);
        event.setOrganizer(organizer);
        return event;
    }

    private RegistrationEntity registrationOf(EventEntity event, String firstName, String lastName, String email) {
        UserEntity participant = new UserEntity();
        participant.setId(16L);
        participant.setFirstName(firstName);
        participant.setLastName(lastName);
        participant.setEmail(email);
        participant.setPasswordHash("hash");
        participant.setStatus(UserStatus.ACTIVE);

        RegistrationEntity registration = new RegistrationEntity();
        registration.setId(1L);
        registration.setRegistrationCode(UUID.randomUUID());
        registration.setEvent(event);
        registration.setParticipant(participant);
        registration.setStatus(RegistrationStatus.CONFIRMED);
        registration.setConfirmedAt(OffsetDateTime.now().minusDays(1));
        return registration;
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
