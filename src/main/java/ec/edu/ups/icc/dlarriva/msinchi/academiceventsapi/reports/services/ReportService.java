package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.reports.services;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.reports.dtos.ReportFilterDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services.UserDetailsImpl;

public interface ReportService {

    /**
     * Solo el ORGANIZER dueño del evento o ADMIN (OwnershipValidator).
     */
    byte[] generateRegistrationsPdf(Long eventId, ReportFilterDto filters, UserDetailsImpl currentUser);

    byte[] generateRegistrationsExcel(Long eventId, ReportFilterDto filters, UserDetailsImpl currentUser);
}
