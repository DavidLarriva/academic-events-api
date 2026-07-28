package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.enums;

/**
 * Debe coincidir exactamente con el CHECK chk_events_status de V1__initial_schema_and_data.sql.
 */
public enum EventStatus {
    DRAFT,
    PUBLISHED,
    FINISHED,
    CANCELLED
}
