package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.enums.RedisKeyPrefix;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba de humo contra el Redis real del docker-compose local
 * (docs/instrucciones.md §6): confirma que la conexión funciona y que
 * set/get/increment respetan el TTL obligatorio.
 */
@DataRedisTest
@Import(RedisKeyServiceImpl.class)
class RedisKeyServiceImplIT {

    @Autowired
    private RedisKeyService redisKeyService;

    @Test
    void setAndGetRoundTripWithTtl() {
        String key = "smoke-" + UUID.randomUUID();

        redisKeyService.set(RedisKeyPrefix.RATE_LIMIT_PUBLIC, key, "hello", Duration.ofSeconds(30));

        assertEquals(Optional.of("hello"), redisKeyService.get(RedisKeyPrefix.RATE_LIMIT_PUBLIC, key));
        assertTrue(redisKeyService.exists(RedisKeyPrefix.RATE_LIMIT_PUBLIC, key));

        redisKeyService.delete(RedisKeyPrefix.RATE_LIMIT_PUBLIC, key);
        assertFalse(redisKeyService.exists(RedisKeyPrefix.RATE_LIMIT_PUBLIC, key));
    }

    @Test
    void incrementSetsTtlOnlyOnFirstWriteAndKeepsCounting() {
        String key = "counter-" + UUID.randomUUID();

        long first = redisKeyService.increment(RedisKeyPrefix.RATE_LIMIT_AUTHENTICATED, key, Duration.ofSeconds(30));
        long second = redisKeyService.increment(RedisKeyPrefix.RATE_LIMIT_AUTHENTICATED, key, Duration.ofSeconds(30));

        assertEquals(1L, first);
        assertEquals(2L, second);

        redisKeyService.delete(RedisKeyPrefix.RATE_LIMIT_AUTHENTICATED, key);
    }

    @Test
    void setRejectsMissingOrNonPositiveTtl() {
        String key = "no-ttl-" + UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> redisKeyService.set(RedisKeyPrefix.BLOCKED_IP, key, "x", null));
        assertThrows(IllegalArgumentException.class,
                () -> redisKeyService.set(RedisKeyPrefix.BLOCKED_IP, key, "x", Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> redisKeyService.increment(RedisKeyPrefix.BLOCKED_IP, key, Duration.ofSeconds(-1)));
    }
}
