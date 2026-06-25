package com.example.mcpgateway.identity.domain.repository;

import com.example.mcpgateway.identity.domain.model.User;
import com.example.mcpgateway.identity.domain.model.UserRole;
import com.example.mcpgateway.identity.domain.model.UserStatus;

import java.util.List;
import java.util.Optional;

public interface UserRepository {
    Optional<User> findById(String id);
    Optional<User> findByUsername(String username);
    List<User> findAll();
    User save(User user);
    void updateStatus(String id, String status);
    long count();
}
