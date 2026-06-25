package com.example.mcpgateway.gateway.application.service;

import com.example.mcpgateway.gateway.domain.repository.McpServerAuthRepository;
import com.example.mcpgateway.identity.infrastructure.security.BcryptPasswordService;
import org.springframework.stereotype.Service;

@Service
public class McpServerAuthVerifier {
    private final McpServerAuthRepository authRepo;
    private final BcryptPasswordService passwords;

    public McpServerAuthVerifier(McpServerAuthRepository authRepo, BcryptPasswordService passwords) {
        this.authRepo = authRepo;
        this.passwords = passwords;
    }

    public boolean verify(long serverId, String presentedKey) {
        if (presentedKey == null || presentedKey.isBlank())
            return false;
        return authRepo.findByServerId(serverId)
                .map(auth -> passwords.matches(presentedKey, auth.mcpKeyHash()))
                .orElse(false);
    }
}
