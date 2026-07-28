package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.categories.dtos;

import java.time.OffsetDateTime;

public record CategoryResponseDto(
        Long id,
        String name,
        String description,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
