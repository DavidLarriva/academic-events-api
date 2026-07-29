package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.response;

import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * timestamp en OffsetDateTime/UTC explícito (docs/instrucciones.md §14),
 * igual que el resto de la API — no LocalDateTime: sin offset, un cliente no
 * puede saber si esa hora es UTC o la hora local del servidor.
 */
public record ErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        Map<String, String> details
) {

    public static ErrorResponse of(HttpStatus status, String code, String message, String path) {
        return of(status, code, message, path, null);
    }

    public static ErrorResponse of(HttpStatus status, String code, String message, String path,
                                    Map<String, String> details) {
        return new ErrorResponse(OffsetDateTime.now(ZoneOffset.UTC), status.value(), status.getReasonPhrase(),
                code, message, path, details);
    }
}
