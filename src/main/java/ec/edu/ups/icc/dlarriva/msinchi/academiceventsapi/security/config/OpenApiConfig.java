package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registra el esquema Bearer JWT (contexto-materia.md §16.4) para habilitar
 * el botón Authorize en Swagger UI. No agrega el requirement globalmente:
 * cada controlador protegido lo declara con
 * @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME) (§16.5),
 * así los endpoints públicos de AuthController (register/login/refresh/logout)
 * no aparecen con el candado engañosamente.
 */
@Configuration
public class OpenApiConfig {

    public static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI academicEventsOpenApi() {
        SecurityScheme bearerScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");

        return new OpenAPI()
                .info(new Info()
                        .title("Academic Events API")
                        .description("API REST para gestión de eventos académicos: usuarios, categorías, eventos, "
                                + "sesiones, inscripciones y auditoría (docs/instrucciones.md).")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME, bearerScheme));
    }
}
