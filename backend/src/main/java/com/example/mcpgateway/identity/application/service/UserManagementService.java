package com.example.mcpgateway.identity.application.service;

import com.example.mcpgateway.identity.domain.model.User;
import com.example.mcpgateway.identity.domain.model.UserRole;
import com.example.mcpgateway.identity.domain.model.UserStatus;
import com.example.mcpgateway.identity.domain.repository.RefreshTokenRepository;
import com.example.mcpgateway.identity.domain.repository.UserRepository;
import com.example.mcpgateway.identity.infrastructure.security.BcryptPasswordService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class UserManagementService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final BcryptPasswordService passwords;

    public UserManagementService(UserRepository users, RefreshTokenRepository refreshTokens, BcryptPasswordService passwords) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwords = passwords;
    }

    public List<UserView> listUsers() {
        return users.findAll().stream().map(UserManagementService::view).toList();
    }

    @Transactional
    public UserView createUser(String username, String password, UserRole role) {
        if (users.findByUsername(username).isPresent()) {
            throw new UsernameAlreadyExistsException();
        }
        Instant now = Instant.now();
        User saved = users.save(new User(
                UUID.randomUUID().toString(),
                username,
                passwords.encode(password),
                role,
                UserStatus.ACTIVE,
                now,
                now
        ));
        return view(saved);
    }

    @Transactional
    public void changeStatus(String userId, UserStatus status) {
        users.findById(userId).orElseThrow(UserNotFoundException::new);
        users.updateStatus(userId, status.name());
        if (status == UserStatus.DISABLED) {
            refreshTokens.revokeAllForUser(userId);
        }
    }

    private static UserView view(User user) {
        return new UserView(user.id(), user.username(), user.role(), user.status());
    }

    public record UserView(String id, String username, UserRole role, UserStatus status) {
    }

    public static class UsernameAlreadyExistsException extends RuntimeException {
    }

    public static class UserNotFoundException extends RuntimeException {
    }
}
