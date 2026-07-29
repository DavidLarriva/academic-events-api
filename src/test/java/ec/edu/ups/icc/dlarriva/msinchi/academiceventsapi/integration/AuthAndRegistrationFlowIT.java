package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.integration;

import com.jayway.jsonpath.JsonPath;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.categories.entities.CategoryEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.categories.repositories.CategoryRepository;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.entities.EventEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.enums.EventModality;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.enums.EventStatus;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.events.repositories.EventRepository;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.enums.RedisKeyPrefix;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.enums.RoleName;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.repositories.RoleRepository;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services.RedisKeyService;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.users.entities.UserEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.users.enums.UserStatus;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.users.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flujos críticos de extremo a extremo (docs/instrucciones.md, entregable
 * "Pruebas") contra un Postgres real efímero (Testcontainers, decisión
 * acordada con el usuario: el V1__initial_schema_and_data.sql del profesor
 * usa tipos/constraints específicos de Postgres que H2 no replica fielmente).
 * Redis usa la instancia local de docker-compose (misma convención que
 * RedisKeyServiceImplIT) — no hace falta un contenedor propio para eso.
 * <p>
 * Cada método resetea el contador de rate limiting de /auth/register antes
 * de correr: RateLimitAspect usa RateLimitKeyStrategy.IP para ese endpoint
 * (docs/instrucciones.md §7: 3/hora por IP), y MockMvc siempre reporta
 * "127.0.0.1" como IP de origen, así que sin este reset la 4ª prueba de la
 * clase (o cualquier corrida repetida de ./gradlew test dentro de la misma
 * hora) devolvería 429 en vez de lo que realmente se está probando.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthAndRegistrationFlowIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String TEST_PASSWORD = "Password123*";
    private static final String REGISTER_IP_KEY = "ip:127.0.0.1";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private RedisKeyService redisKeyService;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void resetRegisterRateLimit() {
        redisKeyService.delete(RedisKeyPrefix.RATE_LIMIT_REGISTER, REGISTER_IP_KEY);
    }

    // ---------------------------------------------------------------
    // registro -> login -> endpoint protegido
    // ---------------------------------------------------------------

    @Test
    void registerThenLoginThenAccessProtectedEndpointSucceeds() throws Exception {
        String email = uniqueEmail("flow");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.user.email").value(email));

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, TEST_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn();

        String accessToken = readAccessToken(loginResult);

        mockMvc.perform(get("/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void loginWithWrongPasswordReturnsGenericUnauthorized() throws Exception {
        String email = uniqueEmail("wrongpass");
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, "not-the-real-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Correo o contraseña incorrectos"));
    }

    @Test
    void accessingProtectedEndpointWithoutTokenIsRejected() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------
    // inscripción con cupo agotado
    // ---------------------------------------------------------------

    @Test
    void registeringOnEventWithoutAvailableCapacityReturnsConflict() throws Exception {
        EventEntity event = persistPublishedEvent(0);

        String email = uniqueEmail("nocapacity");
        String accessToken = registerAndLogin(email);

        mockMvc.perform(post("/registrations")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRegistrationBody(event.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT_NO_CAPACITY"));
    }

    @Test
    void registeringOnEventWithAvailableCapacitySucceedsAsPending() throws Exception {
        EventEntity event = persistPublishedEvent(5);

        String email = uniqueEmail("withcapacity");
        String accessToken = registerAndLogin(email);

        mockMvc.perform(post("/registrations")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRegistrationBody(event.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        EventEntity reloaded = eventRepository.findById(event.getId()).orElseThrow();
        assertEquals(5, reloaded.getAvailableCapacity(), "PENDING no debe descontar cupo todavía");
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email)))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, TEST_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        return readAccessToken(loginResult);
    }

    private EventEntity persistPublishedEvent(int availableCapacity) {
        CategoryEntity category = new CategoryEntity();
        category.setName("IT Category " + UUID.randomUUID());
        category.setActive(true);
        category = categoryRepository.save(category);

        UserEntity organizer = new UserEntity();
        organizer.setFirstName("Org");
        organizer.setLastName("Izer");
        organizer.setEmail(uniqueEmail("organizer"));
        organizer.setPasswordHash(passwordEncoder.encode("Whatever123*"));
        organizer.setStatus(UserStatus.ACTIVE);
        organizer.setRoles(Set.of(roleRepository.findByName(RoleName.ORGANIZER).orElseThrow()));
        organizer = userRepository.save(organizer);

        EventEntity event = new EventEntity();
        event.setTitle("Evento IT " + UUID.randomUUID());
        event.setDescription("Evento de prueba de integración");
        event.setModality(EventModality.VIRTUAL);
        event.setVirtualUrl("https://meet.example.test/it");
        event.setCapacity(Math.max(availableCapacity, 1));
        event.setAvailableCapacity(availableCapacity);
        event.setRegistrationStartAt(OffsetDateTime.now().minusDays(1));
        event.setRegistrationEndAt(OffsetDateTime.now().plusDays(5));
        event.setStartAt(OffsetDateTime.now().plusDays(10));
        event.setEndAt(OffsetDateTime.now().plusDays(10).plusHours(2));
        event.setStatus(EventStatus.PUBLISHED);
        event.setOrganizer(organizer);
        event.setCategory(category);
        return eventRepository.save(event);
    }

    private String readAccessToken(MvcResult result) throws Exception {
        String accessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
        assertNotNull(accessToken);
        return accessToken;
    }

    private String uniqueEmail(String label) {
        return label + "-" + UUID.randomUUID() + "@academic.test";
    }

    private String registerBody(String email) {
        return """
                {"firstName":"Flow","lastName":"Tester","email":"%s","password":"%s"}
                """.formatted(email, TEST_PASSWORD);
    }

    private String loginBody(String email, String password) {
        return """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
    }

    private String createRegistrationBody(Long eventId) {
        return """
                {"eventId": %d}
                """.formatted(eventId);
    }
}
