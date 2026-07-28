package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.enums.RedisKeyPrefix;

import java.time.Duration;
import java.util.Optional;

/**
 * Único punto de acceso a Redis del proyecto (docs/instrucciones.md §6).
 * <p>
 * Redis es exclusivamente para información TEMPORAL usada por mecanismos de
 * seguridad y rendimiento (rate limiting, bloqueo temporal de login).
 * Usuarios, eventos, sesiones, inscripciones y cualquier dato principal
 * viven siempre en PostgreSQL — Redis nunca es la fuente de verdad de esos
 * datos, y este componente no expone forma alguna de guardar algo sin TTL:
 * toda escritura (set/increment) exige un Duration positivo.
 */
public interface RedisKeyService {

    void set(RedisKeyPrefix prefix, String key, String value, Duration ttl);

    Optional<String> get(RedisKeyPrefix prefix, String key);

    void delete(RedisKeyPrefix prefix, String key);

    boolean exists(RedisKeyPrefix prefix, String key);

    /**
     * Incrementa atómicamente un contador (rate limiting). Si es la primera
     * escritura de esa clave, además le fija el TTL; en incrementos
     * posteriores el TTL restante no se toca (ventana fija).
     */
    long increment(RedisKeyPrefix prefix, String key, Duration ttl);
}
