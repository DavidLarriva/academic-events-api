package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.reports.services;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.reports.dtos.ReportFilterDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services.UserDetailsImpl;

public interface ReportService {

    /**
     * Solo el ORGANIZER dueño del evento o ADMIN (OwnershipValidator).
     */
    byte[] generateRegistrationsPdf(Long eventId, ReportFilterDto filters, UserDetailsImpl currentUser);

    byte[] generateRegistrationsExcel(Long eventId, ReportFilterDto filters, UserDetailsImpl currentUser);

    /**
     * Solo el PARTICIPANT dueño de la inscripción, sin excepción para ADMIN
     * (docs/instrucciones.pdf sección 13 marca este endpoint específicamente como
     * "Participante propietario", a diferencia de los dos reportes de
     * arriba que sí dicen "Propietario o ADMIN" — decisión acordada con el
     * usuario). Solo para inscripciones CONFIRMED.
     */
    byte[] generateRegistrationCertificate(Long registrationId, UserDetailsImpl currentUser);
}
