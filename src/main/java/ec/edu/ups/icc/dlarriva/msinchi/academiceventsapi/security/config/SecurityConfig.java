package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.config;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.filters.JwtAuthenticationEntryPoint;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.filters.JwtAuthenticationFilter;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services.UserDetailsServiceImpl;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Base de seguridad (contexto-materia.md §12.9) + autorización por roles
 * (§13) + CORS restringido (docs/instrucciones.md §8). Los requestMatchers
 * NO llevan el prefijo /api porque Spring Security evalúa la ruta antes de
 * que se aplique el context-path (§16.3).
 * <p>
 * Endpoints públicos (permitAll): registro/login/refresh/logout (se validan
 * dentro de AuthService, no con el JWT filter), swagger/openapi (documentación,
 * sin datos sensibles) y actuator/health (health check). Todo lo demás exige
 * un access token válido por defecto (.anyRequest().authenticated()) — los
 * módulos de negocio que vengan después no necesitan tocar esta clase para
 * quedar protegidos, solo agregan @PreAuthorize si necesitan restringir por
 * rol además de por autenticación.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
public class SecurityConfig {

    private static final List<String> ALLOWED_METHODS =
            List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    private static final List<String> ALLOWED_HEADERS =
            List.of("Authorization", "Content-Type");

    private final UserDetailsServiceImpl userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final CorsProperties corsProperties;
    private final Environment environment;

    public SecurityConfig(UserDetailsServiceImpl userDetailsService,
                           JwtAuthenticationFilter jwtAuthenticationFilter,
                           JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
                           CorsProperties corsProperties, Environment environment) {
        this.userDetailsService = userDetailsService;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.corsProperties = corsProperties;
        this.environment = environment;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * Auth por Bearer JWT en el header Authorization: el navegador nunca
     * necesita mandar cookies ni Authorization "implícito" cross-origin, así
     * que no hay razón para habilitar credentials — mantenerlo apagado
     * también evita que Spring exija orígenes exactos en vez de patrones.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> allowedOrigins = corsProperties.allowedOrigins();
        boolean isProd = List.of(environment.getActiveProfiles()).contains("prod");
        if (isProd && allowedOrigins.stream().anyMatch("*"::equals)) {
            throw new IllegalStateException(
                    "ALLOWED_ORIGINS no puede ser '*' en producción (docs/instrucciones.md §8)");
        }

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(ALLOWED_METHODS);
        configuration.setAllowedHeaders(ALLOWED_HEADERS);
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(c -> c.configurationSource(corsConfigurationSource()))
                .exceptionHandling(e -> e.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/register", "/auth/login", "/auth/refresh", "/auth/logout").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
