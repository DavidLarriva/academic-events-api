package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Mapea cors.allowed-origins (ALLOWED_ORIGINS, lista separada por comas —
 * Spring Boot separa automáticamente un valor "a,b,c" en List<String>).
 */
@ConfigurationProperties(prefix = "cors")
public record CorsProperties(List<String> allowedOrigins) {
}
