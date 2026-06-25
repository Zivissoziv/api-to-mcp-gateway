package com.example.mcpgateway.identity.application.service;

import com.example.mcpgateway.identity.domain.model.User;
import com.example.mcpgateway.identity.domain.repository.RefreshTokenRepository;
import com.example.mcpgateway.identity.domain.repository.UserRepository;
import com.example.mcpgateway.identity.infrastructure.security.BcryptPasswordService;
import com.example.mcpgateway.identity.infrastructure.security.JwtTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class AuthenticationService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final BcryptPasswordService passwords;
    private final JwtTokenService tokens;
    private final long accessTokenMinutes;
    private final long refreshTokenDays;

    public AuthenticationService(
            UserRepository users,
            RefreshTokenRepository refreshTokens,
            BcryptPasswordService passwords,
            JwtTokenService tokens,
            @Value("${app.security.access-token-minutes}") long accessTokenMinutes,
            @Value("${app.security.refresh-token-days}") long refreshTokenDays
    ) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwords = passwords;
        this.tokens = tokens;
        this.accessTokenMinutes = accessTokenMinutes;
        this.refreshTokenDays = refreshTokenDays;
    }

    @Transactional
    public TokenPair login(String username, String password) {
        User user = users.findByUsername(username)
                .filter(User::isActive)
                .orElseThrow(() -> new InvalidCredentialsException());
        if (!passwords.matches(password, user.passwordHash())) {
            throw new InvalidCredentialsException();
        }
        return issue(user);
    }

    @Transactional
    public TokenPair refresh(String refreshToken) {
        String hash = tokens.hashRefreshToken(refreshToken);
        String userId = refreshTokens.findActiveUserId(hash, Instant.now())
                .orElseThrow(() -> new InvalidRefreshTokenException());
        User user = users.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new InvalidRefreshTokenException());
        refreshTokens.revoke(hash);
        return issue(user);
    }

    public void logout(String refreshToken) {
        refreshTokens.revoke(tokens.hashRefreshToken(refreshToken));
    }

    private TokenPair issue(User user) {
        String refreshToken = tokens.createRefreshToken();
        refreshTokens.save(
                UUID.randomUUID().toString().hashCode() & Long.MAX_VALUE,
                user.id(),
                tokens.hashRefreshToken(refreshToken),
                Instant.now().plus(refreshTokenDays, ChronoUnit.DAYS)
        );
        return new TokenPair(
                tokens.createAccessToken(user),
                refreshToken,
                accessTokenMinutes * 60
        );
    }

    public record TokenPair(String accessToken, String refreshToken, long expiresInSeconds) {
    }

    public static class InvalidCredentialsException extends RuntimeException {
    }

    public static class InvalidRefreshTokenException extends RuntimeException {
    }
}
