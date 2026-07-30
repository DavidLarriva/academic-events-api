package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.utils;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.config.JwtProperties;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.services.UserDetailsImpl;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Genera y valida access tokens (claim "type":"access") y refresh tokens
 * (claim "type":"refresh", "jti"=token_id de refresh_tokens) en HS256
 * (contexto-materia.md sección 12.4, sección 15.2).
 */
@Component
public class JwtUtil {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final JwtProperties jwtProperties;
    private final SecretKey key;

    public JwtUtil(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.key = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(UserDetailsImpl principal) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(jwtProperties.accessExpiration());
        String roles = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        return Jwts.builder()
                .subject(String.valueOf(principal.getId()))
                .claim(CLAIM_EMAIL, principal.getEmail())
                .claim(CLAIM_NAME, principal.getFullName())
                .claim(CLAIM_ROLES, roles)
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public String generateRefreshToken(Long userId, UUID tokenId) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(jwtProperties.refreshExpiration());

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .id(tokenId.toString())
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public boolean validateAccessToken(String token) {
        return hasType(token, TYPE_ACCESS);
    }

    public boolean validateRefreshToken(String token) {
        return hasType(token, TYPE_REFRESH);
    }

    private boolean hasType(String token, String expectedType) {
        try {
            Claims claims = parseClaims(token);
            return expectedType.equals(claims.get(CLAIM_TYPE, String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String getEmailFromToken(String token) {
        return parseClaims(token).get(CLAIM_EMAIL, String.class);
    }

    public Long getUserIdFromToken(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    public UUID getJtiFromToken(String token) {
        return UUID.fromString(parseClaims(token).getId());
    }

    public long getAccessExpirationMillis() {
        return jwtProperties.accessExpiration();
    }

    public long getRefreshExpirationMillis() {
        return jwtProperties.refreshExpiration();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
