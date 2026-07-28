package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.enums;

/**
 * Debe coincidir exactamente con el CHECK chk_registrations_status de V1__initial_schema_and_data.sql.
 */
public enum RegistrationStatus {
    PENDING,
    CONFIRMED,
    REJECTED,
    CANCELLED
}
