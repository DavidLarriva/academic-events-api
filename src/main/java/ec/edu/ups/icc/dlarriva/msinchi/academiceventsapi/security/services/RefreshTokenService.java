package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.entities.RefreshTokenEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.users.entities.UserEntity;

import java.util.UUID;

public interface RefreshTokenService {

    /**
     * Hashea y persiste un refresh token recién emitido.
     */
    RefreshTokenEntity issue(UserEntity user, UUID tokenId, String rawToken, String createdByIp);

    /**
     * Valida jti+hash+revocación+expiración+usuario activo. Si el token ya
     * estaba revocado (reuso de un token rotado o cerrado), revoca en cadena
     * todas las sesiones activas del usuario antes de lanzar la excepción
     * (posible robo).
     */
    RefreshTokenEntity validateActive(String rawToken, UUID tokenId);

    /**
     * Marca oldToken como reemplazado por newTokenId (rotación).
     */
    void rotate(RefreshTokenEntity oldToken, UUID newTokenId);

    /**
     * Revoca un único refresh token (logout de esa sesión).
     */
    void revoke(RefreshTokenEntity token);

    /**
     * Logout: revoca por tokenId si existe y sigue activo; si no existe o ya
     * estaba revocado, no hace nada (idempotente, sin lanzar excepción ni
     * activar la cadena de revocación por reuso que sí aplica en refresh()).
     */
    void revokeIfActive(UUID tokenId);

    /**
     * Revoca todos los refresh tokens activos del usuario (posible robo por
     * reuso de un token ya rotado). Pública y con su propia transacción
     * (REQUIRES_NEW) para que persista aunque el llamador, dentro de
     * validateActive(), termine lanzando una excepción que haría rollback de
     * su propia transacción.
     */
    void revokeAllActiveForUser(Long userId);
}
