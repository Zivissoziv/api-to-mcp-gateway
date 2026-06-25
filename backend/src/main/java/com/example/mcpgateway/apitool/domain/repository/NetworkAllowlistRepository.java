package com.example.mcpgateway.apitool.domain.repository;

import com.example.mcpgateway.apitool.domain.model.NetworkAllowlist;

import java.util.List;
import java.util.Optional;

public interface NetworkAllowlistRepository {
    List<NetworkAllowlist> findAll();
    Optional<NetworkAllowlist> findById(long id);
    NetworkAllowlist save(NetworkAllowlist entry);
    void deleteById(long id);
}
