package com.example.mcpgateway.system.domain;

public record SystemStatus(String status, String application) {

    public static SystemStatus ready() {
        return new SystemStatus("READY", "mcp-gateway");
    }
}
