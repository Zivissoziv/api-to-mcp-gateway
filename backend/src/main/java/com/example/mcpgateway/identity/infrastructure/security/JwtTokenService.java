package com.example.mcpgateway.identity.infrastructure.security;

import com.example.mcpgateway.identity.domain.model.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenService {
    private final SecretKey key;
    private final long accessTokenMinutes;
    public JwtTokenService(@Value("${app.security.jwt-secret}") String secret,
                           @Value("${app.security.access-token-minutes}") long accessTokenMinutes) {
        this.key=Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenMinutes=accessTokenMinutes;
    }
    public String createAccessToken(User user) {
        Instant now=Instant.now();
        return Jwts.builder().subject(user.id()).claim("username",user.username())
                .claim("role",user.role().name()).issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenMinutes,ChronoUnit.MINUTES)))
                .signWith(key).compact();
    }
    public String createRefreshToken() { return UUID.randomUUID()+"."+UUID.randomUUID(); }
    public String hashRefreshToken(String token) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    public AuthenticatedUser parse(String token) {
        var claims=Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return new AuthenticatedUser(claims.getSubject(),
                claims.get("username",String.class),claims.get("role",String.class));
    }
    public record AuthenticatedUser(String userId, String username, String role){}
}
