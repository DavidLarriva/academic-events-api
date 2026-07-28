package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services;

/**
 * Bloqueo temporal tras intentos fallidos de login (docs/instrucciones.md
 * §6). Se trackea por IP y por correo en paralelo: el que primero llegue al
 * umbral bloquea esa clave, independientemente del otro.
 */
public interface LoginAttemptService {

    /**
     * Lanza UnauthorizedException con el mismo código/mensaje genérico que
     * unas credenciales inválidas si la IP o el correo están bloqueados —
     * nunca revela que la causa fue un bloqueo y no una contraseña
     * incorrecta.
     */
    void checkNotBlocked(String ip, String email);

    /**
     * Cuenta un intento fallido para IP y correo; si alguno llega al umbral,
     * lo bloquea.
     */
    void recordFailure(String ip, String email);

    /**
     * Login exitoso: limpia el conteo de fallos previos de esa IP/correo.
     */
    void recordSuccess(String ip, String email);
}
