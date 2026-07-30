package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain.UnauthorizedException;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.config.JwtProperties;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.entities.RefreshTokenEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.repositories.RefreshTokenRepository;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.users.entities.UserEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.users.enums.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Foco: "token viejo queda inválido tras usarse" (rotación, contexto-materia.md
 * sección 15.3/sección 15.4). validateActive() sobre un token ya revocado (reuso) debe
 * tratarse como posible robo y revocar TODA la familia de tokens activos del
 * usuario, vía el proxy self (@Lazy) para que la transacción REQUIRES_NEW
 * sobreviva al rollback de la excepción que se lanza en el mismo método.
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplTest {

    private static final long REFRESH_EXPIRATION_MILLIS = 604_800_000L; // 7 días

    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private RefreshTokenService self;

    private RefreshTokenServiceImpl refreshTokenService;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties("test-secret", 900_000L, REFRESH_EXPIRATION_MILLIS);
        refreshTokenService = new RefreshTokenServiceImpl(refreshTokenRepository, jwtProperties, self);
    }

    // ---------------------------------------------------------------
    // issue
    // ---------------------------------------------------------------

    @Test
    void issuePersistsHashedTokenWithExpiryAndCreatedByIp() {
        UserEntity user = userEntity(1L, UserStatus.ACTIVE);
        UUID tokenId = UUID.randomUUID();
        when(refreshTokenRepository.save(any(RefreshTokenEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RefreshTokenEntity saved = refreshTokenService.issue(user, tokenId, "raw-token", "203.0.113.5");

        assertEquals(tokenId, saved.getTokenId());
        assertEquals(user, saved.getUser());
        assertEquals(sha256Hex("raw-token"), saved.getTokenHash());
        assertEquals("203.0.113.5", saved.getCreatedByIp());
        assertNotNull(saved.getExpiresAt());
        assertTrue(saved.getExpiresAt().isAfter(OffsetDateTime.now().plusDays(6)));
    }

    // ---------------------------------------------------------------
    // validateActive
    // ---------------------------------------------------------------

    @Test
    void validateActiveAcceptsAFreshMatchingUnexpiredToken() {
        UUID tokenId = UUID.randomUUID();
        RefreshTokenEntity entity = activeEntity(tokenId, "raw-token", UserStatus.ACTIVE);
        when(refreshTokenRepository.findByTokenId(tokenId)).thenReturn(Optional.of(entity));

        RefreshTokenEntity result = refreshTokenService.validateActive("raw-token", tokenId);

        assertEquals(entity, result);
        verify(self, never()).revokeAllActiveForUser(any());
    }

    @Test
    void validateActiveRejectsUnknownTokenId() {
        UUID tokenId = UUID.randomUUID();
        when(refreshTokenRepository.findByTokenId(tokenId)).thenReturn(Optional.empty());

        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> refreshTokenService.validateActive("raw-token", tokenId));

        assertEquals("INVALID_REFRESH_TOKEN", ex.getCode());
    }

    @Test
    void validateActiveOnAlreadyRevokedTokenRevokesWholeFamilyThroughSelfProxyAndRejects() {
        UUID tokenId = UUID.randomUUID();
        UserEntity user = userEntity(5L, UserStatus.ACTIVE);
        RefreshTokenEntity entity = activeEntity(tokenId, "reused-raw-token", UserStatus.ACTIVE);
        entity.setUser(user);
        entity.setRevokedAt(OffsetDateTime.now().minusMinutes(1));
        when(refreshTokenRepository.findByTokenId(tokenId)).thenReturn(Optional.of(entity));

        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> refreshTokenService.validateActive("reused-raw-token", tokenId));

        assertEquals("INVALID_REFRESH_TOKEN", ex.getCode());
        verify(self).revokeAllActiveForUser(5L);
    }

    @Test
    void validateActiveRejectsExpiredTokenWithoutRevokingFamily() {
        UUID tokenId = UUID.randomUUID();
        RefreshTokenEntity entity = activeEntity(tokenId, "raw-token", UserStatus.ACTIVE);
        entity.setExpiresAt(OffsetDateTime.now().minusSeconds(1));
        when(refreshTokenRepository.findByTokenId(tokenId)).thenReturn(Optional.of(entity));

        assertThrows(UnauthorizedException.class, () -> refreshTokenService.validateActive("raw-token", tokenId));

        verify(self, never()).revokeAllActiveForUser(any());
    }

    @Test
    void validateActiveRejectsHashMismatch() {
        UUID tokenId = UUID.randomUUID();
        RefreshTokenEntity entity = activeEntity(tokenId, "the-real-raw-token", UserStatus.ACTIVE);
        when(refreshTokenRepository.findByTokenId(tokenId)).thenReturn(Optional.of(entity));

        assertThrows(UnauthorizedException.class,
                () -> refreshTokenService.validateActive("a-tampered-different-token", tokenId));
    }

    @Test
    void validateActiveRejectsWhenOwningUserIsNotActive() {
        UUID tokenId = UUID.randomUUID();
        RefreshTokenEntity entity = activeEntity(tokenId, "raw-token", UserStatus.BLOCKED);
        when(refreshTokenRepository.findByTokenId(tokenId)).thenReturn(Optional.of(entity));

        assertThrows(UnauthorizedException.class, () -> refreshTokenService.validateActive("raw-token", tokenId));
    }

    // ---------------------------------------------------------------
    // rotate: el mecanismo que efectivamente invalida el token viejo
    // ---------------------------------------------------------------

    @Test
    void rotateMarksOldTokenAsRevokedAndLinksReplacement() {
        RefreshTokenEntity oldToken = activeEntity(UUID.randomUUID(), "old-raw-token", UserStatus.ACTIVE);
        UUID newTokenId = UUID.randomUUID();
        when(refreshTokenRepository.save(oldToken)).thenReturn(oldToken);

        refreshTokenService.rotate(oldToken, newTokenId);

        assertEquals(newTokenId, oldToken.getReplacedByTokenId());
        assertNotNull(oldToken.getRevokedAt());
        verify(refreshTokenRepository).save(oldToken);
    }

    // ---------------------------------------------------------------
    // revoke / revokeIfActive / revokeAllActiveForUser
    // ---------------------------------------------------------------

    @Test
    void revokeSetsRevokedAtOnlyOnce() {
        RefreshTokenEntity token = activeEntity(UUID.randomUUID(), "raw-token", UserStatus.ACTIVE);
        when(refreshTokenRepository.save(token)).thenReturn(token);

        refreshTokenService.revoke(token);
        OffsetDateTime firstRevokedAt = token.getRevokedAt();
        refreshTokenService.revoke(token);

        assertEquals(firstRevokedAt, token.getRevokedAt());
        verify(refreshTokenRepository, org.mockito.Mockito.times(1)).save(token);
    }

    @Test
    void revokeIfActiveRevokesAnUnrevokedToken() {
        UUID tokenId = UUID.randomUUID();
        RefreshTokenEntity token = activeEntity(tokenId, "raw-token", UserStatus.ACTIVE);
        when(refreshTokenRepository.findByTokenId(tokenId)).thenReturn(Optional.of(token));

        refreshTokenService.revokeIfActive(tokenId);

        assertNotNull(token.getRevokedAt());
    }

    @Test
    void revokeIfActiveIsANoOpForUnknownTokenId() {
        UUID tokenId = UUID.randomUUID();
        when(refreshTokenRepository.findByTokenId(tokenId)).thenReturn(Optional.empty());

        refreshTokenService.revokeIfActive(tokenId);

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void revokeIfActiveDoesNotOverwriteAnAlreadyRevokedTimestamp() {
        UUID tokenId = UUID.randomUUID();
        RefreshTokenEntity token = activeEntity(tokenId, "raw-token", UserStatus.ACTIVE);
        OffsetDateTime originalRevokedAt = OffsetDateTime.now().minusDays(1);
        token.setRevokedAt(originalRevokedAt);
        when(refreshTokenRepository.findByTokenId(tokenId)).thenReturn(Optional.of(token));

        refreshTokenService.revokeIfActive(tokenId);

        assertEquals(originalRevokedAt, token.getRevokedAt());
    }

    @Test
    void revokeAllActiveForUserRevokesEveryActiveTokenOfThatUser() {
        RefreshTokenEntity tokenA = activeEntity(UUID.randomUUID(), "a", UserStatus.ACTIVE);
        RefreshTokenEntity tokenB = activeEntity(UUID.randomUUID(), "b", UserStatus.ACTIVE);
        when(refreshTokenRepository.findAllByUser_IdAndRevokedAtIsNull(5L)).thenReturn(List.of(tokenA, tokenB));

        refreshTokenService.revokeAllActiveForUser(5L);

        assertNotNull(tokenA.getRevokedAt());
        assertNotNull(tokenB.getRevokedAt());
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private RefreshTokenEntity activeEntity(UUID tokenId, String rawToken, UserStatus ownerStatus) {
        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.setTokenId(tokenId);
        entity.setUser(userEntity(1L, ownerStatus));
        entity.setTokenHash(sha256Hex(rawToken));
        entity.setExpiresAt(OffsetDateTime.now().plusDays(7));
        return entity;
    }

    private UserEntity userEntity(Long id, UserStatus status) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setStatus(status);
        return user;
    }

    /** Replica exacta del hash privado de RefreshTokenServiceImpl (SHA-256/hex, sin salt). */
    private static String sha256Hex(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
