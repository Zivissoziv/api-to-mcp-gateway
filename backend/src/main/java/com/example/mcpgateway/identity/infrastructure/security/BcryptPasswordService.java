package com.example.mcpgateway.identity.infrastructure.security;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BcryptPasswordService {
    private final PasswordEncoder encoder;
    public BcryptPasswordService(PasswordEncoder encoder) { this.encoder = encoder; }
    public String encode(String rawPassword) { return encoder.encode(rawPassword); }
    public boolean matches(String rawPassword, String encodedPassword) {
        return encoder.matches(rawPassword, encodedPassword);
    }
}
