package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mapea swagger.username / swagger.password (SWAGGER_USERNAME/SWAGGER_PASSWORD,
 * docs/instrucciones.md §16). Credenciales de evaluación para HTTP Basic
 * sobre /swagger-ui/** y /v3/api-docs/** en prod (docs/instrucciones.md §11)
 * — un único usuario en memoria, sin relación con la tabla users ni con JWT.
 */
@ConfigurationProperties(prefix = "swagger")
public record SwaggerAuthProperties(String username, String password) {
}
