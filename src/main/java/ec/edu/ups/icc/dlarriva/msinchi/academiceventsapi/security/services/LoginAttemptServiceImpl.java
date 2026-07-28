package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain.UnauthorizedException;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.enums.RedisKeyPrefix;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class LoginAttemptServiceImpl implements LoginAttemptService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration ATTEMPT_TRACKING_WINDOW = Duration.ofMinutes(15);
    private static final Duration BLOCK_DURATION = Duration.ofMinutes(15);

    private static final String BLOCKED_MARKER = "1";

    private final RedisKeyService redisKeyService;

    public LoginAttemptServiceImpl(RedisKeyService redisKeyService) {
        this.redisKeyService = redisKeyService;
    }

    @Override
    public void checkNotBlocked(String ip, String email) {
        boolean blocked = redisKeyService.exists(RedisKeyPrefix.BLOCKED_IP, ip)
                || redisKeyService.exists(RedisKeyPrefix.BLOCKED_USER, email);
        if (blocked) {
            throw new UnauthorizedException("INVALID_CREDENTIALS", "Correo o contraseña incorrectos");
        }
    }

    @Override
    public void recordFailure(String ip, String email) {
        long ipAttempts = redisKeyService.increment(RedisKeyPrefix.FAILED_LOGIN_IP, ip, ATTEMPT_TRACKING_WINDOW);
        long userAttempts = redisKeyService.increment(RedisKeyPrefix.FAILED_LOGIN_USER, email, ATTEMPT_TRACKING_WINDOW);

        if (ipAttempts >= MAX_FAILED_ATTEMPTS) {
            redisKeyService.set(RedisKeyPrefix.BLOCKED_IP, ip, BLOCKED_MARKER, BLOCK_DURATION);
        }
        if (userAttempts >= MAX_FAILED_ATTEMPTS) {
            redisKeyService.set(RedisKeyPrefix.BLOCKED_USER, email, BLOCKED_MARKER, BLOCK_DURATION);
        }
    }

    @Override
    public void recordSuccess(String ip, String email) {
        redisKeyService.delete(RedisKeyPrefix.FAILED_LOGIN_IP, ip);
        redisKeyService.delete(RedisKeyPrefix.FAILED_LOGIN_USER, email);
    }
}
