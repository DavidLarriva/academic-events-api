package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.dtos;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.enums.EventModality;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.enums.EventStatus;

import java.time.OffsetDateTime;

public record EventResponseDto(
        Long id,
        String title,
        String description,
        EventModality modality,
        String location,
        String virtualUrl,
        Integer capacity,
        Integer availableCapacity,
        OffsetDateTime registrationStartAt,
        OffsetDateTime registrationEndAt,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        EventStatus status,
        Long organizerId,
        Long categoryId,
        Long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
