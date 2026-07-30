package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.enums;

/**
 * Cómo se compone la clave de Redis para un @RateLimit (docs/instrucciones.pdf
 * sección 7: el identificador es "una dirección IP" o "el usuario autenticado";
 * login además combina IP + correo).
 */
public enum RateLimitKeyStrategy {

    /** IP de origen únicamente (registro, endpoints públicos). */
    IP,

    /** Usuario autenticado (@AuthenticationPrincipal), no la IP. */
    AUTHENTICATED_USER,

    /** IP + correo recibido en el body de LoginRequestDto (solo login). */
    IP_AND_LOGIN_EMAIL
}
