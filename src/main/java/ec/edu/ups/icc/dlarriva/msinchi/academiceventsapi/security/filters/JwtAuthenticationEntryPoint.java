package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.filters;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.response.ErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Se dispara antes de llegar a cualquier controlador (fuera del alcance de
 * GlobalExceptionHandler), por eso arma el mismo ErrorResponse a mano
 * (contexto-materia.md §12.8).
 * Ojo: Spring Boot 4 usa Jackson 3 (tools.jackson.*) como ObjectMapper por
 * defecto, no com.fasterxml.jackson.databind (Jackson 2) — no hay bean de
 * ese tipo legado a menos que se agregue explícitamente.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException, ServletException {
        ErrorResponse body = ErrorResponse.of(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                "No autenticado o token inválido", request.getRequestURI());

        // getWriter() usaría ISO-8859-1 por defecto y rompería tildes/eñes;
        // se escriben bytes UTF-8 directamente para evitarlo.
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getOutputStream().write(objectMapper.writeValueAsBytes(body));
    }
}
