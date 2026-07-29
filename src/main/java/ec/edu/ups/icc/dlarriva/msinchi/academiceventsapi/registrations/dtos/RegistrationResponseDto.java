package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.dtos;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.registrations.enums.RegistrationStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RegistrationResponseDto(
        Long id,
        UUID registrationCode,
        Long eventId,
        Long participantId,
        RegistrationStatus status,
        OffsetDateTime registeredAt,
        OffsetDateTime statusUpdatedAt,
        OffsetDateTime confirmedAt,
        OffsetDateTime cancelledAt,
        Long version
) {
}
