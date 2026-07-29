package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.aspects;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain.TooManyRequestsException;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.annotations.RateLimit;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.dtos.LoginRequestDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.entities.RoleEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.enums.RateLimitKeyStrategy;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.enums.RedisKeyPrefix;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.enums.RoleName;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.users.entities.UserEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.users.enums.UserStatus;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services.RedisKeyService;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services.UserDetailsImpl;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.utils.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Comportamiento al superar el límite (docs/instrucciones.md §7): incrementa
 * atómicamente el contador y, al pasarse del límite, corta la ejecución con
 * TooManyRequestsException(429) sin invocar el método real, calculando
 * Retry-After desde el TTL restante de la clave en Redis.
 */
@ExtendWith(MockitoExtension.class)
class RateLimitAspectTest {

    @Mock
    private RedisKeyService redisKeyService;
    @Mock
    private ClientIpResolver clientIpResolver;
    @Mock
    private HttpServletRequest request;
    @Mock
    private ProceedingJoinPoint joinPoint;

    private RateLimitAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new RateLimitAspect(redisKeyService, clientIpResolver, request);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void proceedsWhenUnderLimit() throws Throwable {
        RateLimit rateLimit = rateLimit(RedisKeyPrefix.RATE_LIMIT_PUBLIC, 60, 60, RateLimitKeyStrategy.IP);
        when(clientIpResolver.resolve(request)).thenReturn("203.0.113.10");
        when(redisKeyService.increment(RedisKeyPrefix.RATE_LIMIT_PUBLIC, "ip:203.0.113.10", Duration.ofSeconds(60)))
                .thenReturn(1L);
        when(joinPoint.proceed()).thenReturn("controller-result");

        Object result = aspect.enforce(joinPoint, rateLimit);

        assertEquals("controller-result", result);
    }

    @Test
    void throwsTooManyRequestsWithRetryAfterFromRemainingTtlOnceLimitIsExceeded() throws Throwable {
        RateLimit rateLimit = rateLimit(RedisKeyPrefix.RATE_LIMIT_PUBLIC, 60, 60, RateLimitKeyStrategy.IP);
        when(clientIpResolver.resolve(request)).thenReturn("203.0.113.10");
        when(redisKeyService.increment(RedisKeyPrefix.RATE_LIMIT_PUBLIC, "ip:203.0.113.10", Duration.ofSeconds(60)))
                .thenReturn(61L);
        when(redisKeyService.getExpireSeconds(RedisKeyPrefix.RATE_LIMIT_PUBLIC, "ip:203.0.113.10"))
                .thenReturn(Optional.of(37L));

        TooManyRequestsException ex = assertThrows(TooManyRequestsException.class,
                () -> aspect.enforce(joinPoint, rateLimit));

        assertEquals("RATE_LIMIT_EXCEEDED", ex.getCode());
        assertEquals(37L, ex.getRetryAfterSeconds());
        verify(joinPoint, never()).proceed();
    }

    @Test
    void fallsBackToConfiguredWindowWhenTtlIsAlreadyGoneAtExcessTime() throws Throwable {
        RateLimit rateLimit = rateLimit(RedisKeyPrefix.RATE_LIMIT_PUBLIC, 60, 60, RateLimitKeyStrategy.IP);
        when(clientIpResolver.resolve(request)).thenReturn("203.0.113.10");
        when(redisKeyService.increment(any(), any(), any())).thenReturn(61L);
        when(redisKeyService.getExpireSeconds(any(), any())).thenReturn(Optional.empty());

        TooManyRequestsException ex = assertThrows(TooManyRequestsException.class,
                () -> aspect.enforce(joinPoint, rateLimit));

        assertEquals(60L, ex.getRetryAfterSeconds());
    }

    @Test
    void authenticatedUserStrategyKeysByPrincipalId() throws Throwable {
        RateLimit rateLimit = rateLimit(RedisKeyPrefix.RATE_LIMIT_AUTHENTICATED, 120, 60,
                RateLimitKeyStrategy.AUTHENTICATED_USER);
        UserDetailsImpl principal = principalWithId(42L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        when(redisKeyService.increment(RedisKeyPrefix.RATE_LIMIT_AUTHENTICATED, "user:42", Duration.ofSeconds(60)))
                .thenReturn(1L);

        aspect.enforce(joinPoint, rateLimit);

        verify(redisKeyService).increment(RedisKeyPrefix.RATE_LIMIT_AUTHENTICATED, "user:42", Duration.ofSeconds(60));
    }

    @Test
    void authenticatedUserStrategyFailsFastWithoutAnAuthenticatedPrincipal() {
        RateLimit rateLimit = rateLimit(RedisKeyPrefix.RATE_LIMIT_AUTHENTICATED, 120, 60,
                RateLimitKeyStrategy.AUTHENTICATED_USER);

        assertThrows(IllegalStateException.class, () -> aspect.enforce(joinPoint, rateLimit));
    }

    @Test
    void ipAndLoginEmailStrategyCombinesIpWithNormalizedEmailFromLoginRequestArgument() throws Throwable {
        RateLimit rateLimit = rateLimit(RedisKeyPrefix.RATE_LIMIT_LOGIN, 5, 60, RateLimitKeyStrategy.IP_AND_LOGIN_EMAIL);
        when(clientIpResolver.resolve(request)).thenReturn("203.0.113.10");
        LoginRequestDto loginRequest = new LoginRequestDto();
        loginRequest.setEmail("  User@Academic.Test ");
        loginRequest.setPassword("whatever");
        when(joinPoint.getArgs()).thenReturn(new Object[] {loginRequest});
        when(redisKeyService.increment(eq(RedisKeyPrefix.RATE_LIMIT_LOGIN),
                eq("ip:203.0.113.10:email:user@academic.test"), eq(Duration.ofSeconds(60)))).thenReturn(1L);

        aspect.enforce(joinPoint, rateLimit);

        verify(redisKeyService).increment(RedisKeyPrefix.RATE_LIMIT_LOGIN,
                "ip:203.0.113.10:email:user@academic.test", Duration.ofSeconds(60));
    }

    @Test
    void ipAndLoginEmailStrategyFailsFastWithoutALoginRequestArgument() {
        RateLimit rateLimit = rateLimit(RedisKeyPrefix.RATE_LIMIT_LOGIN, 5, 60, RateLimitKeyStrategy.IP_AND_LOGIN_EMAIL);
        when(joinPoint.getArgs()).thenReturn(new Object[] {});

        assertThrows(IllegalStateException.class, () -> aspect.enforce(joinPoint, rateLimit));
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /**
     * lenient(): según el camino probado, enforce() puede lanzar en
     * resolveKey() antes de leer prefix()/limit()/windowSeconds() (casos
     * "fail fast" de AUTHENTICATED_USER/IP_AND_LOGIN_EMAIL sin contexto
     * válido), así que no todos los stubs se usan en todos los tests.
     */
    private RateLimit rateLimit(RedisKeyPrefix prefix, int limit, long windowSeconds, RateLimitKeyStrategy strategy) {
        RateLimit rateLimit = org.mockito.Mockito.mock(RateLimit.class);
        org.mockito.Mockito.lenient().when(rateLimit.prefix()).thenReturn(prefix);
        org.mockito.Mockito.lenient().when(rateLimit.limit()).thenReturn(limit);
        org.mockito.Mockito.lenient().when(rateLimit.windowSeconds()).thenReturn(windowSeconds);
        org.mockito.Mockito.lenient().when(rateLimit.keyStrategy()).thenReturn(strategy);
        return rateLimit;
    }

    private UserDetailsImpl principalWithId(Long id) {
        RoleEntity role = new RoleEntity();
        role.setId(1L);
        role.setName(RoleName.PARTICIPANT);
        role.setDescription("PARTICIPANT");

        UserEntity user = new UserEntity();
        user.setId(id);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEmail("test" + id + "@academic.test");
        user.setPasswordHash("hash");
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of(role));
        return UserDetailsImpl.build(user);
    }
}
