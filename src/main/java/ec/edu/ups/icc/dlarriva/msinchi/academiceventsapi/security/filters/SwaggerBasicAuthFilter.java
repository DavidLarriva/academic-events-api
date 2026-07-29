package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.filters;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.config.SwaggerAuthProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Verificación de HTTP Basic propia y autocontenida, sin pasar por
 * AuthenticationManager/DaoAuthenticationProvider de Spring Security: con
 * múltiples SecurityFilterChain en la app, .userDetailsService(...) /
 * .authenticationManager(...) sobre la chain de swagger terminaban
 * resolviendo (o interfiriendo con) el AuthenticationManager global atado a
 * la tabla users real, rechazando incluso credenciales correctas. Esta
 * credencial es un único usuario de evaluación por variables de entorno
 * (SwaggerAuthProperties), sin relación con roles/JWT/tabla users, así que
 * no hay necesidad real de toda esa maquinaria — solo comparar usuario y
 * hash de contraseña.
 * <p>
 * NO es @Component a propósito: JwtAuthenticationFilter sí lo es porque su
 * lógica es "aditiva" (si no hay token válido, no hace nada y sigue) — sin
 * ese registro doble le da igual. Este filtro en cambio RECHAZA
 * activamente sin credenciales válidas; si además de registrarse scoped
 * (SecurityConfig#swaggerFilterChain, vía addFilterBefore) quedara también
 * @Component, Spring Boot lo auto-registraría GLOBALMENTE para /* (como
 * cualquier bean Filter), bloqueando toda la API con 401 sin importar el
 * SecurityFilterChain — pasó exactamente eso antes de sacar la anotación.
 * SecurityConfig lo instancia a mano (new), no vía inyección.
 */
public class SwaggerBasicAuthFilter extends OncePerRequestFilter {

    private static final String REALM = "WWW-Authenticate";
    private static final String BASIC_PREFIX = "Basic ";

    private final SwaggerAuthProperties swaggerAuthProperties;
    private final PasswordEncoder passwordEncoder;
    private final String encodedPassword;

    /**
     * BCryptPasswordEncoder propio, no el bean compartido de SecurityConfig:
     * ese @Bean vive DENTRO de SecurityConfig, que a su vez depende de este
     * filtro para registrarlo en la chain de swagger -> ciclo. Es un
     * encoder sin estado, no hay ninguna necesidad real de que sea la MISMA
     * instancia.
     */
    public SwaggerBasicAuthFilter(SwaggerAuthProperties swaggerAuthProperties) {
        this.swaggerAuthProperties = swaggerAuthProperties;
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.encodedPassword = passwordEncoder.encode(swaggerAuthProperties.password());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isAuthorized(request)) {
            chain.doFilter(request, response);
            return;
        }
        response.setHeader(REALM, "Basic realm=\"Swagger\"");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    private boolean isAuthorized(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(header) || !header.startsWith(BASIC_PREFIX)) {
            return false;
        }
        String[] credentials = decode(header.substring(BASIC_PREFIX.length()));
        if (credentials == null) {
            return false;
        }
        return swaggerAuthProperties.username().equals(credentials[0])
                && passwordEncoder.matches(credentials[1], encodedPassword);
    }

    private String[] decode(String base64Credentials) {
        try {
            String decoded = new String(Base64.getDecoder().decode(base64Credentials), StandardCharsets.UTF_8);
            int separatorIndex = decoded.indexOf(':');
            if (separatorIndex < 0) {
                return null;
            }
            return new String[] {decoded.substring(0, separatorIndex), decoded.substring(separatorIndex + 1)};
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
