package com.example.mcpgateway.gateway.domain.model;

import java.time.Instant;

public record IpCallStats(
        String clientIp,
        long callCount,
        Instant lastCallAt
) {}
