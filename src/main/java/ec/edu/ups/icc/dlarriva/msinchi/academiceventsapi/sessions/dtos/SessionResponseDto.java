package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.sessions.dtos;

import java.time.OffsetDateTime;

public record SessionResponseDto(
        Long id,
        Long eventId,
        String title,
        String description,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        String location,
        String virtualUrl,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
