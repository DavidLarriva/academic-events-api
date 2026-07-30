package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.utils;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Conversión UTC -> zona de negocio (docs/instrucciones.pdf sección 14: "Zona de
 * negocio: America/Guayaquil. Almacenamiento de instantes: UTC. Formato de
 * intercambio: ISO 8601"). Se usa SOLO al mostrar datos a un humano (reportes
 * PDF/Excel de los prompts 17/18) o similar — nunca en entidades ni en los
 * *ResponseDto de la API, que siempre viajan en UTC/ISO-8601 sin ambigüedad
 * (el cliente localiza para mostrar, no el servidor).
 */
public final class BusinessTimeZoneConverter {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Guayaquil");

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private BusinessTimeZoneConverter() {
    }

    /**
     * Mismo instante, representado en America/Guayaquil.
     */
    public static ZonedDateTime toBusinessZone(OffsetDateTime instant) {
        if (instant == null) {
            return null;
        }
        return instant.atZoneSameInstant(BUSINESS_ZONE);
    }

    /**
     * Listo para imprimir en un reporte (ej. "29/07/2026 10:15"); no está
     * pensado para volver a parsear ni para respuestas JSON.
     */
    public static String formatForDisplay(OffsetDateTime instant) {
        ZonedDateTime local = toBusinessZone(instant);
        return local == null ? null : local.format(DISPLAY_FORMAT);
    }
}
