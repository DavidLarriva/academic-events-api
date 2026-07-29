package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.models;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.enums.RegistrationStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Dominio puro, sin anotaciones JPA (contexto-materia.md §4.2).
 */
public class RegistrationModel {

    private Long id;
    private UUID registrationCode;
    private Long eventId;
    private Long participantId;
    private RegistrationStatus status;
    private OffsetDateTime registeredAt;
    private OffsetDateTime statusUpdatedAt;
    private OffsetDateTime confirmedAt;
    private OffsetDateTime cancelledAt;
    private Long version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getRegistrationCode() {
        return registrationCode;
    }

    public void setRegistrationCode(UUID registrationCode) {
        this.registrationCode = registrationCode;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public Long getParticipantId() {
        return participantId;
    }

    public void setParticipantId(Long participantId) {
        this.participantId = participantId;
    }

    public RegistrationStatus getStatus() {
        return status;
    }

    public void setStatus(RegistrationStatus status) {
        this.status = status;
    }

    public OffsetDateTime getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(OffsetDateTime registeredAt) {
        this.registeredAt = registeredAt;
    }

    public OffsetDateTime getStatusUpdatedAt() {
        return statusUpdatedAt;
    }

    public void setStatusUpdatedAt(OffsetDateTime statusUpdatedAt) {
        this.statusUpdatedAt = statusUpdatedAt;
    }

    public OffsetDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(OffsetDateTime confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public OffsetDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(OffsetDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
