package com.example.mcpgateway.gateway.domain.repository;

import com.example.mcpgateway.gateway.domain.model.GatewayCall;

public interface GatewayCallRepository {
    void save(GatewayCall call);
}
