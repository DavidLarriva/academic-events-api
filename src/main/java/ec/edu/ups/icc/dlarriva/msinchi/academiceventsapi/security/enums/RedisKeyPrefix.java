package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.enums;

/**
 * Prefijos de claves de Redis (docs/instrucciones.pdf sección 6: "Las claves deberán
 * utilizar prefijos que identifiquen su finalidad, como blocked-user:").
 * Todo lo que vive detrás de estos prefijos es temporal: rate limiting y
 * bloqueos de login. Redis NUNCA almacena usuarios, eventos, inscripciones
 * ni ningún dato principal — eso vive siempre en PostgreSQL.
 * <p>
 * Los RATE_LIMIT_* corresponden exactamente a las 5 categorías de
 * instrucciones.pdf sección 7. RATE_LIMIT_REPORTS todavía no se usa en ningún
 * endpoint (el módulo de reportes no existe aún) pero queda lista para
 * cuando se implemente.
 */
public enum RedisKeyPrefix {

    BLOCKED_USER("blocked-user:"),
    BLOCKED_IP("blocked-ip:"),
    FAILED_LOGIN_USER("failed-login-user:"),
    FAILED_LOGIN_IP("failed-login-ip:"),
    RATE_LIMIT_LOGIN("rate-limit-login:"),
    RATE_LIMIT_REGISTER("rate-limit-register:"),
    RATE_LIMIT_PUBLIC("rate-limit-public:"),
    RATE_LIMIT_AUTHENTICATED("rate-limit-authenticated:"),
    RATE_LIMIT_REPORTS("rate-limit-reports:");

    private final String prefix;

    RedisKeyPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }
}
