package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain.UnauthorizedException;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.config.JwtProperties;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.entities.RefreshTokenEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.repositories.RefreshTokenRepository;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.users.entities.UserEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.users.enums.UserStatus;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

/**
 * token_hash = SHA-256 sin salt: el refresh token ya es un JWT firmado de
 * alta entropía (no es una contraseña adivinable), y esta verificación corre
 * en cada /refresh y /logout, así que se evita el costo de un hash lento
 * tipo bcrypt/Argon2 (contexto-materia.md sección 15.3/sección 15.4).
 */
@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final String MENSAJE_INVALIDO = "Refresh token inválido o expirado";
    private static final String CODIGO_INVALIDO = "INVALID_REFRESH_TOKEN";

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final RefreshTokenService self;

    /**
     * self es el propio bean (proxy de Spring), inyectado con @Lazy para
     * poder invocar revokeAllActiveForUser a través del proxy y que
     * @Transactional(REQUIRES_NEW) surta efecto: una llamada directa
     * (this.revokeAllActiveForUser(...)) es auto-invocación y Spring AOP no
     * la intercepta, por lo que correría en la misma transacción que
     * validateActive() y se perdería con el rollback al lanzar la excepción.
     */
    public RefreshTokenServiceImpl(RefreshTokenRepository refreshTokenRepository, JwtProperties jwtProperties,
                                    @Lazy RefreshTokenService self) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProperties = jwtProperties;
        this.self = self;
    }

    @Override
    @Transactional
    public RefreshTokenEntity issue(UserEntity user, UUID tokenId, String rawToken, String createdByIp) {
        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.setTokenId(tokenId);
        entity.setUser(user);
        entity.setTokenHash(hash(rawToken));
        entity.setExpiresAt(OffsetDateTime.now().plus(Duration.ofMillis(jwtProperties.refreshExpiration())));
        entity.setCreatedByIp(createdByIp);
        return refreshTokenRepository.save(entity);
    }

    @Override
    @Transactional
    public RefreshTokenEntity validateActive(String rawToken, UUID tokenId) {
        RefreshTokenEntity entity = refreshTokenRepository.findByTokenId(tokenId)
                .orElseThrow(() -> new UnauthorizedException(CODIGO_INVALIDO, MENSAJE_INVALIDO));

        if (entity.getRevokedAt() != null) {
            // Reuso de un token ya rotado/revocado: se trata como posible robo.
            // Vía self (proxy) para que la transacción REQUIRES_NEW persista
            // aunque esta misma llamada termine lanzando la excepción.
            self.revokeAllActiveForUser(entity.getUser().getId());
            throw new UnauthorizedException(CODIGO_INVALIDO, MENSAJE_INVALIDO);
        }

        if (entity.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new UnauthorizedException(CODIGO_INVALIDO, MENSAJE_INVALIDO);
        }

        if (!hash(rawToken).equals(entity.getTokenHash())) {
            throw new UnauthorizedException(CODIGO_INVALIDO, MENSAJE_INVALIDO);
        }

        if (entity.getUser().getStatus() != UserStatus.ACTIVE) {
            throw new UnauthorizedException(CODIGO_INVALIDO, MENSAJE_INVALIDO);
        }

        return entity;
    }

    @Override
    @Transactional
    public void rotate(RefreshTokenEntity oldToken, UUID newTokenId) {
        oldToken.setReplacedByTokenId(newTokenId);
        oldToken.setRevokedAt(OffsetDateTime.now());
        refreshTokenRepository.save(oldToken);
    }

    @Override
    @Transactional
    public void revoke(RefreshTokenEntity token) {
        if (token.getRevokedAt() == null) {
            token.setRevokedAt(OffsetDateTime.now());
            refreshTokenRepository.save(token);
        }
    }

    @Override
    @Transactional
    public void revokeIfActive(UUID tokenId) {
        refreshTokenRepository.findByTokenId(tokenId)
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> token.setRevokedAt(OffsetDateTime.now()));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllActiveForUser(Long userId) {
        OffsetDateTime now = OffsetDateTime.now();
        refreshTokenRepository.findAllByUser_IdAndRevokedAtIsNull(userId)
                .forEach(token -> token.setRevokedAt(now));
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
    }
}
