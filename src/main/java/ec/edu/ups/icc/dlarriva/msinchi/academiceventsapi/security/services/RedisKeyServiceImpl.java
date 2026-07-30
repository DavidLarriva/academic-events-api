package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.enums.RedisKeyPrefix;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class RedisKeyServiceImpl implements RedisKeyService {

    private final StringRedisTemplate redisTemplate;

    public RedisKeyServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void set(RedisKeyPrefix prefix, String key, String value, Duration ttl) {
        requireTtl(ttl);
        redisTemplate.opsForValue().set(buildKey(prefix, key), value, ttl);
    }

    @Override
    public Optional<String> get(RedisKeyPrefix prefix, String key) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(buildKey(prefix, key)));
    }

    @Override
    public void delete(RedisKeyPrefix prefix, String key) {
        redisTemplate.delete(buildKey(prefix, key));
    }

    @Override
    public boolean exists(RedisKeyPrefix prefix, String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(buildKey(prefix, key)));
    }

    @Override
    public long increment(RedisKeyPrefix prefix, String key, Duration ttl) {
        requireTtl(ttl);
        String fullKey = buildKey(prefix, key);
        Long count = redisTemplate.opsForValue().increment(fullKey);
        if (count == null) {
            count = 1L;
        }
        if (count == 1L) {
            redisTemplate.expire(fullKey, ttl);
        }
        return count;
    }

    @Override
    public Optional<Long> getExpireSeconds(RedisKeyPrefix prefix, String key) {
        Long seconds = redisTemplate.getExpire(buildKey(prefix, key), TimeUnit.SECONDS);
        if (seconds == null || seconds < 0) {
            return Optional.empty();
        }
        return Optional.of(seconds);
    }

    private void requireTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException(
                    "Toda escritura en Redis debe declarar un TTL positivo (docs/instrucciones.pdf sección 6)");
        }
    }

    private String buildKey(RedisKeyPrefix prefix, String key) {
        return prefix.getPrefix() + key;
    }
}
