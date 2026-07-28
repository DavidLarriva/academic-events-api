package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mapea jwt.secret / jwt.access-expiration / jwt.refresh-expiration
 * (application-dev.yaml / application-prod.yaml). refreshExpiration todavía
 * no se usa (el refresh token va en un prompt siguiente).
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, long accessExpiration, long refreshExpiration) {
}
