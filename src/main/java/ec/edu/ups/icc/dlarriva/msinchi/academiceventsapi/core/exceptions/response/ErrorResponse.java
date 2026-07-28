package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.response;

import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Map;

public record ErrorResponse(
        LocalDateTime timestamp,
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
        return new ErrorResponse(LocalDateTime.now(), status.value(), status.getReasonPhrase(),
                code, message, path, details);
    }
}
