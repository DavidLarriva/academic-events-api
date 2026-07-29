package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.config;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.filters.CorrelationIdFilter;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.filters.JwtAccessDeniedHandler;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.filters.JwtAuthenticationEntryPoint;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.filters.JwtAuthenticationFilter;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.filters.SwaggerBasicAuthFilter;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services.UserDetailsServiceImpl;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
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
 * Dos SecurityFilterChain: swaggerFilterChain (@Order 1) captura primero
 * /swagger-ui/**, /swagger-ui.html y /v3/api-docs/** con HTTP Basic contra
 * un único usuario en memoria SOLO en prod (docs/instrucciones.md §11); en
 * dev queda abierto. filterChain (@Order 2, sin cambios de fondo) maneja
 * todo lo demás con JWT: registro/login/refresh/logout públicos,
 * /actuator/health público, el resto de /actuator/** exige ADMIN
 * (docs/instrucciones.md §12), y cualquier otra ruta exige un access token
 * válido por defecto.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class, SwaggerAuthProperties.class})
public class SecurityConfig {

    private static final List<String> ALLOWED_METHODS =
            List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    private static final List<String> ALLOWED_HEADERS =
            List.of("Authorization", "Content-Type");
    private static final String[] SWAGGER_PATHS = {"/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**"};

    private final UserDetailsServiceImpl userDetailsService;
    private final CorrelationIdFilter correlationIdFilter;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    private final CorsProperties corsProperties;
    private final SwaggerAuthProperties swaggerAuthProperties;
    private final Environment environment;

    public SecurityConfig(UserDetailsServiceImpl userDetailsService, CorrelationIdFilter correlationIdFilter,
                           JwtAuthenticationFilter jwtAuthenticationFilter,
                           JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
                           JwtAccessDeniedHandler jwtAccessDeniedHandler, CorsProperties corsProperties,
                           SwaggerAuthProperties swaggerAuthProperties, Environment environment) {
        this.userDetailsService = userDetailsService;
        this.correlationIdFilter = correlationIdFilter;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.jwtAccessDeniedHandler = jwtAccessDeniedHandler;
        this.corsProperties = corsProperties;
        this.swaggerAuthProperties = swaggerAuthProperties;
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
        if (isProd() && allowedOrigins.stream().anyMatch("*"::equals)) {
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

    /**
     * Chain independiente, evaluada antes que la principal (@Order 1): en
     * prod exige HTTP Basic (verificado por SwaggerBasicAuthFilter, propio y
     * autocontenido — ver esa clase para el porqué de no usar httpBasic()/
     * AuthenticationManager acá) contra un único usuario de evaluación por
     * variables de entorno, completamente ajeno a la tabla users y al JWT.
     * En dev queda abierto para no pedir credenciales en desarrollo local.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain swaggerFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(SWAGGER_PATHS)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        if (isProd()) {
            // new, no bean: ver el porqué en el Javadoc de la clase.
            http.addFilterBefore(new SwaggerBasicAuthFilter(swaggerAuthProperties),
                    UsernamePasswordAuthenticationFilter.class);
        }

        return http.build();
    }

    /**
     * Endpoints públicos (permitAll): registro/login/refresh/logout (se validan
     * dentro de AuthService, no con el JWT filter) y actuator/health (health
     * check, sin detalles internos por config de Actuator). El resto de
     * /actuator/** exige ADMIN. Todo lo demás exige un access token válido
     * por defecto (.anyRequest().authenticated()) — los módulos de negocio
     * no necesitan tocar esta clase para quedar protegidos, solo agregan
     * @PreAuthorize si necesitan restringir por rol además de por autenticación.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(c -> c.configurationSource(corsConfigurationSource()))
                .exceptionHandling(e -> e.authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/register", "/auth/login", "/auth/refresh", "/auth/logout").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // Necesario para que un 403 de "/actuator/**" (regla a nivel de
                        // filtro, no @PreAuthorize) se muestre tal cual: al denegar,
                        // AccessDeniedHandler hace sendError(403) -> forward interno a
                        // /error, y en esa segunda pasada por la cadena
                        // JwtAuthenticationFilter ya no vuelve a correr (OncePerRequestFilter
                        // lo marca "already filtered" sobre el mismo request) -> sin esto,
                        // /error caía en anyRequest().authenticated() con el SecurityContext
                        // ya limpio y terminaba devolviendo 401 en vez de 403.
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .authenticationProvider(authenticationProvider())
                // Orden importa: JwtAuthenticationFilter.class recién queda "conocido"
                // (con posición registrada) después de esta primera llamada, así que
                // solo entonces se lo puede usar como referencia en la siguiente.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(correlationIdFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    private boolean isProd() {
        return List.of(environment.getActiveProfiles()).contains("prod");
    }
}
