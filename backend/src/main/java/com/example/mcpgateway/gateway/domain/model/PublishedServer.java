package com.example.mcpgateway.gateway.domain.model;

import java.util.List;

public record PublishedServer(
        long id,
        String code,
        String name,
        String description,
        List<PublishedTool> tools
) {}
