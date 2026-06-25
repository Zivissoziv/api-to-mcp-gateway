package com.example.mcpgateway.apitool.application.service;

import com.example.mcpgateway.apitool.domain.model.NetworkAllowlist;
import com.example.mcpgateway.apitool.domain.model.PatternType;
import com.example.mcpgateway.apitool.domain.repository.NetworkAllowlistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class NetworkAllowlistService {
    private final NetworkAllowlistRepository allowlist;
    public NetworkAllowlistService(NetworkAllowlistRepository allowlist) { this.allowlist = allowlist; }

    public List<NetworkAllowlist> list() { return allowlist.findAll(); }

    @Transactional
    public NetworkAllowlist add(String pattern, PatternType patternType, String description, String createdBy) {
        Instant now = Instant.now();
        return allowlist.save(new NetworkAllowlist(
                null,
                pattern, patternType, description, true, createdBy, now));
    }

    @Transactional
    public void delete(long id) { allowlist.deleteById(id); }
}
