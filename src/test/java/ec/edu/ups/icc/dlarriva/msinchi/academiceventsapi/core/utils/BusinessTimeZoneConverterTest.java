package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.utils;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BusinessTimeZoneConverterTest {

    @Test
    void toBusinessZoneConvertsUtcToGuayaquilPreservingTheSameInstant() {
        OffsetDateTime utcNoon = OffsetDateTime.of(2026, 7, 29, 12, 0, 0, 0, ZoneOffset.UTC);

        ZonedDateTime businessTime = BusinessTimeZoneConverter.toBusinessZone(utcNoon);

        assertEquals(utcNoon.toInstant(), businessTime.toInstant());
        assertEquals(7, businessTime.getHour());
        assertEquals(BusinessTimeZoneConverter.BUSINESS_ZONE, businessTime.getZone());
    }

    @Test
    void toBusinessZoneReturnsNullForNullInput() {
        assertNull(BusinessTimeZoneConverter.toBusinessZone(null));
    }

    @Test
    void formatForDisplayRendersGuayaquilLocalTime() {
        OffsetDateTime utc = OffsetDateTime.of(2026, 7, 29, 15, 30, 0, 0, ZoneOffset.UTC);

        String formatted = BusinessTimeZoneConverter.formatForDisplay(utc);

        assertEquals("29/07/2026 10:30", formatted);
    }

    @Test
    void formatForDisplayReturnsNullForNullInput() {
        assertNull(BusinessTimeZoneConverter.formatForDisplay(null));
    }
}
