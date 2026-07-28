package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Resolución de IP de origen, compartida entre AuthController (created_by_ip
 * de refresh_tokens) y RateLimitAspect (identificador de rate limiting).
 */
@Component
public class ClientIpResolver {

    public String resolve(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
