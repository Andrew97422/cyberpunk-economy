package ru.andrew.mainserver.auth.token;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.andrew.mainserver.account.entity.Account;
import ru.andrew.mainserver.config.SecurityProperties;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final SecurityProperties securityProperties;

    public String generate(Account account, Long sessionId, long ttlSeconds) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(ttlSeconds);

        return Jwts.builder()
                .subject(account.getPublicName())
                .claim("accountId", account.getId())
                .claim("role", account.getRole().name())
                .claim("sessionId", sessionId)
                .claim("authorities", Set.of("ROLE_" + account.getRole().name()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(getSigningKey())
                .compact();
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public Long extractAccountId(String token) {
        Object value = extractAllClaims(token).get("accountId");
        return value == null ? null : Long.valueOf(value.toString());
    }

    public Long extractSessionId(String token) {
        Object value = extractAllClaims(token).get("sessionId");
        return value == null ? null : Long.valueOf(value.toString());
    }

    public String extractRole(String token) {
        Object value = extractAllClaims(token).get("role");
        return value == null ? null : value.toString();
    }

    public boolean isValid(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return claims.getExpiration() != null && claims.getExpiration().after(new Date());
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(securityProperties.getJwtSecret());
        return Keys.hmacShaKeyFor(keyBytes);
    }
}