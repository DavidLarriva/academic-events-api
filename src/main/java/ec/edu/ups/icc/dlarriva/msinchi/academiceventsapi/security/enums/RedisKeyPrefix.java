package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.enums;

/**
 * Prefijos de claves de Redis (docs/instrucciones.md §6: "Las claves deberán
 * utilizar prefijos que identifiquen su finalidad, como blocked-user:").
 * Todo lo que vive detrás de estos prefijos es temporal: rate limiting y
 * bloqueos de login. Redis NUNCA almacena usuarios, eventos, inscripciones
 * ni ningún dato principal — eso vive siempre en PostgreSQL.
 */
public enum RedisKeyPrefix {

    BLOCKED_USER("blocked-user:"),
    BLOCKED_IP("blocked-ip:"),
    RATE_LIMIT_USER("rate-limit-user:"),
    RATE_LIMIT_IP("rate-limit-ip:");

    private final String prefix;

    RedisKeyPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }
}
