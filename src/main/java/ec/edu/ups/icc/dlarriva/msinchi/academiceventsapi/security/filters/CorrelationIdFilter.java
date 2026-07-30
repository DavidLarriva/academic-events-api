package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Genera un UUID por request (docs/instrucciones.pdf sección 10, módulo de
 * auditoría: "correlation_id ... para cruzar un registro de auditoría con
 * logs técnicos de esa misma solicitud"). Queda en tres lugares: MDC (para
 * que logging.pattern.level de application.yaml lo imprima en TODAS las
 * líneas de log de esta solicitud), atributo de request (lo lee
 * AuditServiceImpl para guardarlo en audit_logs.correlation_id) y el header
 * de respuesta X-Correlation-Id (el cliente también puede reportarlo).
 * Corre antes que JwtAuthenticationFilter para cubrir también los intentos
 * de login fallidos.
 */
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String ATTRIBUTE_NAME = "correlationId";
    private static final String MDC_KEY = "correlationId";
    private static final String RESPONSE_HEADER = "X-Correlation-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String correlationId = UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE_NAME, correlationId);
        response.setHeader(RESPONSE_HEADER, correlationId);
        MDC.put(MDC_KEY, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
