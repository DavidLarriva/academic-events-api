package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.filters;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.response.ErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Análogo a JwtAuthenticationEntryPoint pero para 403: reglas a nivel de
 * filtro en SecurityConfig (ej. "/actuator/**".hasRole("ADMIN"), a
 * diferencia de @PreAuthorize) deniegan ANTES de llegar a un controlador,
 * fuera del alcance de GlobalExceptionHandler. Sin este handler propio,
 * Spring Security usa AccessDeniedHandlerImpl por defecto, que hace
 * response.sendError(403) -> Spring Boot reenvía internamente a /error
 * (BasicErrorController) -> formato JSON distinto al ErrorResponse uniforme
 * del resto de la API (docs/instrucciones.pdf sección 10), y además ese forward
 * interno reevalúa la cadena de filtros con el SecurityContext ya limpio
 * (JwtAuthenticationFilter es OncePerRequestFilter, no vuelve a correr sobre
 * el mismo request) — terminaba devolviendo 401 en vez de 403. Escribir la
 * respuesta acá mismo, directo, evita el sendError() y por lo tanto ese
 * forward por completo.
 */
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JwtAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                        AccessDeniedException accessDeniedException) throws IOException, ServletException {
        ErrorResponse body = ErrorResponse.of(HttpStatus.FORBIDDEN, "FORBIDDEN",
                "No tiene permisos para realizar esta acción", request.getRequestURI());

        // getWriter() usaría ISO-8859-1 por defecto y rompería tildes/eñes;
        // se escriben bytes UTF-8 directamente para evitarlo.
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getOutputStream().write(objectMapper.writeValueAsBytes(body));
    }
}
