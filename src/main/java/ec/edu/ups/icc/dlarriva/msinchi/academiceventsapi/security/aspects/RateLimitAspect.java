package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.aspects;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain.TooManyRequestsException;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.annotations.RateLimit;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.dtos.LoginRequestDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.enums.RateLimitKeyStrategy;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services.RedisKeyService;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services.UserDetailsImpl;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.utils.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Aplica @RateLimit sobre cualquier método de controlador
 * (docs/instrucciones.pdf sección 7): incrementa atómicamente el contador en Redis
 * (RedisKeyService, INCR+EXPIRE) y, si se supera el límite, corta la
 * ejecución lanzando TooManyRequestsException (429 + Retry-After, manejado
 * por GlobalExceptionHandler) sin llegar al método real.
 */
@Aspect
@Component
public class RateLimitAspect {

    private final RedisKeyService redisKeyService;
    private final ClientIpResolver clientIpResolver;
    private final HttpServletRequest request;

    public RateLimitAspect(RedisKeyService redisKeyService, ClientIpResolver clientIpResolver,
                            HttpServletRequest request) {
        this.redisKeyService = redisKeyService;
        this.clientIpResolver = clientIpResolver;
        this.request = request;
    }

    @Around("@annotation(rateLimit)")
    public Object enforce(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String key = resolveKey(rateLimit.keyStrategy(), joinPoint);
        Duration window = Duration.ofSeconds(rateLimit.windowSeconds());

        long count = redisKeyService.increment(rateLimit.prefix(), key, window);
        if (count > rateLimit.limit()) {
            long retryAfterSeconds = redisKeyService.getExpireSeconds(rateLimit.prefix(), key)
                    .orElse(rateLimit.windowSeconds());
            throw new TooManyRequestsException("RATE_LIMIT_EXCEEDED",
                    "Demasiadas solicitudes, inténtalo de nuevo más tarde", retryAfterSeconds);
        }

        return joinPoint.proceed();
    }

    private String resolveKey(RateLimitKeyStrategy strategy, ProceedingJoinPoint joinPoint) {
        return switch (strategy) {
            case IP -> "ip:" + clientIpResolver.resolve(request);
            case AUTHENTICATED_USER -> "user:" + currentUserId();
            case IP_AND_LOGIN_EMAIL ->
                    "ip:" + clientIpResolver.resolve(request) + ":email:" + loginEmail(joinPoint);
        };
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetailsImpl principal)) {
            throw new IllegalStateException(
                    "@RateLimit(keyStrategy = AUTHENTICATED_USER) usado en un endpoint sin usuario autenticado");
        }
        return principal.getId();
    }

    private String loginEmail(ProceedingJoinPoint joinPoint) {
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof LoginRequestDto loginRequest) {
                return loginRequest.getEmail().trim().toLowerCase();
            }
        }
        throw new IllegalStateException(
                "@RateLimit(keyStrategy = IP_AND_LOGIN_EMAIL) requiere un parámetro LoginRequestDto");
    }
}
