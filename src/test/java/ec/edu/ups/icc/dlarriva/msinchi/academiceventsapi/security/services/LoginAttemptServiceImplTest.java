package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain.UnauthorizedException;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.enums.RedisKeyPrefix;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Bloqueo temporal tras varios intentos fallidos (docs/instrucciones.pdf sección 6):
 * 5 intentos fallidos en la ventana de 15 minutos bloquean la IP y/o el
 * correo por otros 15 minutos.
 */
@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceImplTest {

    private static final String IP = "203.0.113.10";
    private static final String EMAIL = "user@academic.test";

    @Mock
    private RedisKeyService redisKeyService;

    private LoginAttemptServiceImpl loginAttemptService;

    @BeforeEach
    void setUp() {
        loginAttemptService = new LoginAttemptServiceImpl(redisKeyService);
    }

    @Test
    void checkNotBlockedPassesWhenNeitherIpNorEmailIsBlocked() {
        when(redisKeyService.exists(RedisKeyPrefix.BLOCKED_IP, IP)).thenReturn(false);
        when(redisKeyService.exists(RedisKeyPrefix.BLOCKED_USER, EMAIL)).thenReturn(false);

        loginAttemptService.checkNotBlocked(IP, EMAIL);
    }

    @Test
    void checkNotBlockedRejectsWhenIpIsBlocked() {
        when(redisKeyService.exists(RedisKeyPrefix.BLOCKED_IP, IP)).thenReturn(true);

        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> loginAttemptService.checkNotBlocked(IP, EMAIL));

        assertEquals("INVALID_CREDENTIALS", ex.getCode());
    }

    @Test
    void checkNotBlockedRejectsWhenEmailIsBlockedEvenIfIpIsNot() {
        when(redisKeyService.exists(RedisKeyPrefix.BLOCKED_IP, IP)).thenReturn(false);
        when(redisKeyService.exists(RedisKeyPrefix.BLOCKED_USER, EMAIL)).thenReturn(true);

        assertThrows(UnauthorizedException.class, () -> loginAttemptService.checkNotBlocked(IP, EMAIL));
    }

    @Test
    void recordFailureBelowThresholdOnlyIncrementsWithoutBlocking() {
        when(redisKeyService.increment(eq(RedisKeyPrefix.FAILED_LOGIN_IP), eq(IP), any())).thenReturn(3L);
        when(redisKeyService.increment(eq(RedisKeyPrefix.FAILED_LOGIN_USER), eq(EMAIL), any())).thenReturn(2L);

        loginAttemptService.recordFailure(IP, EMAIL);

        verify(redisKeyService, never()).set(eq(RedisKeyPrefix.BLOCKED_IP), any(), any(), any());
        verify(redisKeyService, never()).set(eq(RedisKeyPrefix.BLOCKED_USER), any(), any(), any());
    }

    @Test
    void recordFailureBlocksIpOnceItReachesFiveAttempts() {
        when(redisKeyService.increment(eq(RedisKeyPrefix.FAILED_LOGIN_IP), eq(IP), any())).thenReturn(5L);
        when(redisKeyService.increment(eq(RedisKeyPrefix.FAILED_LOGIN_USER), eq(EMAIL), any())).thenReturn(1L);

        loginAttemptService.recordFailure(IP, EMAIL);

        verify(redisKeyService).set(eq(RedisKeyPrefix.BLOCKED_IP), eq(IP), eq("1"), eq(Duration.ofMinutes(15)));
        verify(redisKeyService, never()).set(eq(RedisKeyPrefix.BLOCKED_USER), any(), any(), any());
    }

    @Test
    void recordFailureBlocksEmailOnceItReachesFiveAttempts() {
        when(redisKeyService.increment(eq(RedisKeyPrefix.FAILED_LOGIN_IP), eq(IP), any())).thenReturn(1L);
        when(redisKeyService.increment(eq(RedisKeyPrefix.FAILED_LOGIN_USER), eq(EMAIL), any())).thenReturn(5L);

        loginAttemptService.recordFailure(IP, EMAIL);

        verify(redisKeyService).set(eq(RedisKeyPrefix.BLOCKED_USER), eq(EMAIL), eq("1"), eq(Duration.ofMinutes(15)));
        verify(redisKeyService, never()).set(eq(RedisKeyPrefix.BLOCKED_IP), any(), any(), any());
    }

    @Test
    void recordFailureBlocksBothWhenBothReachThresholdTogether() {
        when(redisKeyService.increment(eq(RedisKeyPrefix.FAILED_LOGIN_IP), eq(IP), any())).thenReturn(6L);
        when(redisKeyService.increment(eq(RedisKeyPrefix.FAILED_LOGIN_USER), eq(EMAIL), any())).thenReturn(5L);

        loginAttemptService.recordFailure(IP, EMAIL);

        verify(redisKeyService).set(eq(RedisKeyPrefix.BLOCKED_IP), eq(IP), eq("1"), any());
        verify(redisKeyService).set(eq(RedisKeyPrefix.BLOCKED_USER), eq(EMAIL), eq("1"), any());
    }

    @Test
    void recordSuccessClearsBothFailureCounters() {
        loginAttemptService.recordSuccess(IP, EMAIL);

        verify(redisKeyService).delete(RedisKeyPrefix.FAILED_LOGIN_IP, IP);
        verify(redisKeyService).delete(RedisKeyPrefix.FAILED_LOGIN_USER, EMAIL);
    }
}
