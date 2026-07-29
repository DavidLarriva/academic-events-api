package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.reports.services;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain.BadRequestException;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain.ForbiddenException;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain.NotFoundException;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.entities.EventEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.repositories.EventRepository;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.entities.RegistrationEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.enums.RegistrationStatus;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.repositories.RegistrationRepository;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.reports.dtos.ReportFilterDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.reports.generators.RegistrationCertificatePdfGenerator;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.reports.generators.RegistrationExcelReportGenerator;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.reports.generators.RegistrationPdfReportGenerator;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services.OwnershipValidator;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services.UserDetailsImpl;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReportServiceImpl implements ReportService {

    private final EventRepository eventRepository;
    private final RegistrationRepository registrationRepository;
    private final OwnershipValidator ownershipValidator;

    public ReportServiceImpl(EventRepository eventRepository, RegistrationRepository registrationRepository,
                              OwnershipValidator ownershipValidator) {
        this.eventRepository = eventRepository;
        this.registrationRepository = registrationRepository;
        this.ownershipValidator = ownershipValidator;
    }

    @Override
    public byte[] generateRegistrationsPdf(Long eventId, ReportFilterDto filters, UserDetailsImpl currentUser) {
        EventEntity event = findEventAndCheckOwnership(eventId, currentUser);
        List<RegistrationEntity> registrations = findRegistrations(eventId, filters);
        return RegistrationPdfReportGenerator.generate(event, registrations);
    }

    @Override
    public byte[] generateRegistrationsExcel(Long eventId, ReportFilterDto filters, UserDetailsImpl currentUser) {
        EventEntity event = findEventAndCheckOwnership(eventId, currentUser);
        List<RegistrationEntity> registrations = findRegistrations(eventId, filters);
        return RegistrationExcelReportGenerator.generate(event, registrations);
    }

    private EventEntity findEventAndCheckOwnership(Long eventId, UserDetailsImpl currentUser) {
        EventEntity event = eventRepository.findByIdAndDeletedFalse(eventId)
                .orElseThrow(() -> new NotFoundException("EVENT_NOT_FOUND", "Evento no encontrado"));
        ownershipValidator.checkOwnership(event.getOrganizer().getId(), currentUser);
        return event;
    }

    private List<RegistrationEntity> findRegistrations(Long eventId, ReportFilterDto filters) {
        return registrationRepository.findForReport(eventId, filters.getStatus(), filters.getFrom(), filters.getTo());
    }

    @Override
    public byte[] generateRegistrationCertificate(Long registrationId, UserDetailsImpl currentUser) {
        RegistrationEntity registration = registrationRepository.findByIdWithEventAndParticipant(registrationId)
                .orElseThrow(() -> new NotFoundException("REGISTRATION_NOT_FOUND", "Inscripción no encontrada"));

        // A propósito NO usa OwnershipValidator: ese componente siempre deja
        // pasar a ADMIN, y este comprobante es exclusivo del participante
        // dueño (ver Javadoc de ReportService#generateRegistrationCertificate).
        if (!registration.getParticipant().getId().equals(currentUser.getId())) {
            throw new ForbiddenException("NOT_RESOURCE_OWNER", "No tiene permisos sobre esta inscripción");
        }

        if (registration.getStatus() != RegistrationStatus.CONFIRMED) {
            throw new BadRequestException("REGISTRATION_NOT_CONFIRMED",
                    "Solo se puede generar el comprobante de una inscripción CONFIRMED (estado actual: "
                            + registration.getStatus() + ")");
        }

        return RegistrationCertificatePdfGenerator.generate(registration);
    }
}
